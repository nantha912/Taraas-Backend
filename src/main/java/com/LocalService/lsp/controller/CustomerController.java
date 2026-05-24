package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.LoginRequest;
import com.LocalService.lsp.model.Offer;
import com.LocalService.lsp.model.OtpRecord;
import com.LocalService.lsp.model.Transaction;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.OtpRepository;
import com.LocalService.lsp.repository.TransactionRepository;
import com.LocalService.lsp.service.CustomerService;
import com.LocalService.lsp.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.security.auth.login.CredentialNotFoundException;
import java.io.IOException;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private S3Service s3Service;

    /* =====================================================
       🔹 BUYER TIER COMPUTATION (BACKEND SOURCE OF TRUTH)
       ===================================================== */

    private Offer.BuyerCategory calculateBuyerCategory(double totalSpent) {
        if (totalSpent >= 100000) return Offer.BuyerCategory.ELITE;
        if (totalSpent >= 10000) return Offer.BuyerCategory.PRIME;
        if (totalSpent >= 1000) return Offer.BuyerCategory.VERIFIED;
        return Offer.BuyerCategory.NOT_VERIFIED;
    }

    private double calculateTotalSpent(String customerId) {
        if (customerId == null) return 0.0;

        // 🔹 Calculate 12 months back from now
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);

        // 🔹 Fetch only COMPLETED transactions within last 12 months
        List<Transaction> completed =
                transactionRepository.findByCustomerIdAndStatusAndCreatedAtAfter(
                        customerId,
                        "COMPLETED",
                        twelveMonthsAgo
                );

        if (completed == null) return 0.0;

        // 🔹 Sum amounts safely
        return completed.stream()
                .mapToDouble(t -> Optional.ofNullable(t.getAmount()).orElse(0.0))
                .sum();
    }


    /* =====================================================
       EXISTING APIs (SAFE EXTENSION ONLY)
       ===================================================== */

    @GetMapping("/customer/exists")
    public ResponseEntity<?> checkIfEmailExists(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        boolean exists = customerService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }

    /**
     * FETCH: Customer profile with computed buyerCategory
     */
    @GetMapping("/customer/{id}")
    public ResponseEntity<?> getCustomerById(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }

        return customerRepository.findById(id).map(customer -> {

            customerService.attachBuyerCategory(customer);

            Map<String, Object> response = new HashMap<>();
            response.put("id", customer.getId());
            response.put("name", Optional.ofNullable(customer.getName()).orElse("Unknown"));
            response.put("email", Optional.ofNullable(customer.getEmail()).orElse(""));
            response.put("profilePhotoUrl", Optional.ofNullable(customer.getProfilePhotoUrl()).orElse(""));
            // Ensure buyerCategory is never null for the frontend
            response.put("buyerCategory", customer.getBuyerCategory() != null ? customer.getBuyerCategory().name() : "NOT_VERIFIED");
            response.put("totalSpent", Optional.ofNullable(customer.getTotalSpent()).orElse(0.0));
            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * PHOTO UPLOAD
     */
    @PostMapping("/customer/{id}/profile-photo")
    public ResponseEntity<?> uploadCustomerPhoto(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {

        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Optional<Customer> requesterOpt = customerRepository.findByEmail(userEmail);
        
        if (requesterOpt.isEmpty() || !requesterOpt.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only upload photos for your own profile."));
        }

        Optional<Customer> customerOpt = customerRepository.findById(id);
        if (customerOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");

        Customer customer = customerOpt.get();

        try {
            String profilePhotoUrl = customer.getProfilePhotoUrl();
            if (profilePhotoUrl != null && !profilePhotoUrl.isBlank()) {
                s3Service.deleteFile(profilePhotoUrl);
            }

            String photoUrl = s3Service.uploadFile(file, id, "customer_avatars");
            customer.setProfilePhotoUrl(photoUrl);
            customerRepository.save(customer);

            return ResponseEntity.ok(customer);
        } catch (IOException e) {
            logger.error("S3 Upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process photo upload.");
        }
    }

    /**
     * UPDATE PROFILE
     */
    @PutMapping("/customer/{id}")
    public ResponseEntity<?> updateCustomerProfile(
            @PathVariable String id,
            @RequestBody Map<String, String> updates) {

        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }
        if (updates == null) {
            return ResponseEntity.badRequest().body("Updates are required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Optional<Customer> requesterOpt = customerRepository.findByEmail(userEmail);
        
        if (requesterOpt.isEmpty() || !requesterOpt.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only update your own profile."));
        }

        Optional<Customer> customerOpt = customerRepository.findById(id);
        if (customerOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");

        Customer customer = customerOpt.get();

        if (updates.containsKey("name")) customer.setName(updates.get("name"));
        if (updates.containsKey("profilePhotoUrl")) customer.setProfilePhotoUrl(updates.get("profilePhotoUrl"));

        String currentPassword = updates.get("currentPassword");
        String newPassword = updates.get("newPassword");
        if (currentPassword != null && newPassword != null) {
            if (!passwordEncoder.matches(currentPassword, customer.getPassword())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Current password verification failed."));
            }
            customer.setPassword(passwordEncoder.encode(newPassword));
        }

        Customer saved = customerRepository.save(customer);
        saved.setPassword(null);
        return ResponseEntity.ok(saved);
    }

    /**
     * LOGIN: attach buyerCategory
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginCustomer(@RequestBody LoginRequest loginRequest) {
        if (loginRequest == null) {
            return ResponseEntity.badRequest().body("Login request is required");
        }
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();
        if (email == null || password == null) {
            return ResponseEntity.badRequest().body("Email and Password are required");
        }

        try {
            Customer customer = customerService.loginCustomer(email, password);

            double totalSpent = calculateTotalSpent(customer.getId());
            Offer.BuyerCategory buyerCategory = calculateBuyerCategory(totalSpent);

            customer.setPassword(null);

            return ResponseEntity.ok(
                    Map.of(
                            "id", customer.getId(),
                            "name", Optional.ofNullable(customer.getName()).orElse("Unknown"),
                            "email", Optional.ofNullable(customer.getEmail()).orElse(""),
                            "profilePhotoUrl", Optional.ofNullable(customer.getProfilePhotoUrl()).orElse(""),
                            "buyerCategory", buyerCategory.name(),
                            "totalSpent", totalSpent
                    )
            );
        } catch (UserPrincipalNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (CredentialNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            return new ResponseEntity<>("Login failed.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> payload) {
        if (payload == null) return ResponseEntity.badRequest().body("Payload required");
        String email = payload.get("email");
        String newPassword = payload.get("newPassword");

        if (email == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and New Password are required."));
        }
         // 1. Check if email was verified in the last 10 minutes
             Optional<OtpRecord> otpCheck = otpRepository.findByEmail(email);
    
            if (otpCheck.isEmpty() || !otpCheck.get().isVerified()) {
            return ResponseEntity.status(403).body("Email not verified via OTP.");
            }

        try {
            customerService.resetPassword(email, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
        } catch (UserPrincipalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An error occurred during password reset."));
        }
    }
    @PostMapping("/register")
    public ResponseEntity<?> registerCustomer(@RequestBody Customer customer) {
        
        if (customer == null) return ResponseEntity.badRequest().body("Customer data required");
        // Manual validation
        String name = customer.getName();
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("name", "Name is required"));
        }
        String email = customer.getEmail();
        if (email == null || !email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("email", "Valid email is required"));
        }
        String password = customer.getPassword();
        if (password == null || password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("password", "Password must be at least 8 characters"));
        }
        // 1. Check if email was verified in the last 10 minutes
             Optional<OtpRecord> otpCheck = otpRepository.findByEmail(customer.getEmail());
    
            if (otpCheck.isEmpty() || !otpCheck.get().isVerified()) {
            return ResponseEntity.status(403).body("Email not verified via OTP.");
            }

        try {
            Customer registeredCustomer = customerService.registerCustomer(customer);
            registeredCustomer.setPassword(null);
            // Cleanup: Now delete the OTP record so it can't be used again
            otpRepository.deleteByEmail(customer.getEmail());
            return new ResponseEntity<>(registeredCustomer, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        } catch (Exception e) {
            return new ResponseEntity<>("An unexpected error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/customer/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable String id) {
        if (id == null || id.isBlank()) return ResponseEntity.badRequest().body("Customer ID required");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        Optional<Customer> requesterOpt = customerRepository.findByEmail(userEmail);
        
        if (requesterOpt.isEmpty() || !requesterOpt.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only delete your own account."));
        }
        return customerRepository.findById(id).map(customer -> {
            customerRepository.delete(customer);
            return ResponseEntity.ok(Map.of("message", "Customer deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Healthy");
    }
}
