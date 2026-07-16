package com.LocalService.lsp.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.Offer;
import com.LocalService.lsp.model.Transaction;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.TransactionRepository;
import com.LocalService.lsp.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GoogleAuthService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Value("${google.client-id}")
    private String googleClientId;

    public Map<String, Object> authenticateGoogleUser(String tokenString) throws Exception {
        // 1. Initialize Google's cryptographically secure verifier framework
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        // 2. Decode and cryptographically verify the token integrity
        GoogleIdToken idToken = verifier.verify(tokenString);
        if (idToken == null) {
            throw new IllegalArgumentException("Invalid ID Token signature.");
        }

        // 3. Extract safe verified profile identity variables
        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail().trim().toLowerCase();
        String name = (String) payload.get("name");
        
        // 4. BUSINESS LOGIC HANDSHAKE: Find or Create Customer
        Optional<Customer> customerOpt = customerRepository.findByEmail(email);
        Customer customer;
        if (customerOpt.isEmpty()) {
            customer = new Customer();
            customer.setEmail(email);
            customer.setName(name);
            // Set profile photo if provided by Google attributes payload
            String pictureUrl = (String) payload.get("picture");
            if (pictureUrl != null) {
                customer.setProfilePhotoUrl(pictureUrl);
            }
            customer = customerRepository.save(customer);
        } else {
            customer = customerOpt.get();
        }

        // 5. Generate Application JWT Tokens
        String accessToken = tokenProvider.generateToken(customer.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(customer.getEmail());

        // 6. Calculate Buyer Analytics Metrics
        double totalSpent = calculateTotalSpent(customer.getId());
        Offer.BuyerCategory buyerCategory = calculateBuyerCategory(totalSpent);

        // 7. Match exactly the key values expected by the frontend's AuthResponse structure mapping
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken); // Matches 'token' mapping fields
        response.put("token", accessToken);        // Fallback standard matching key
        response.put("refreshToken", refreshToken);
        response.put("name", Optional.ofNullable(customer.getName()).orElse("Unknown"));
        response.put("email", customer.getEmail());
        response.put("id", customer.getId());
        response.put("profilePhotoUrl", Optional.ofNullable(customer.getProfilePhotoUrl()).orElse(""));
        response.put("buyerCategory", buyerCategory != null ? buyerCategory.name() : "NOT_VERIFIED");
        response.put("totalSpent", totalSpent);
        
        return response;
    }

    private double calculateTotalSpent(String customerId) {
        if (customerId == null) return 0.0;
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        List<Transaction> completed = transactionRepository.findByCustomerIdAndStatusAndCreatedAtAfter(
                customerId, "COMPLETED", twelveMonthsAgo
        );
        if (completed == null) return 0.0;
        return completed.stream()
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount() : 0.0)
                .sum();
    }

    private Offer.BuyerCategory calculateBuyerCategory(double totalSpent) {
        if (totalSpent >= 100000) return Offer.BuyerCategory.ELITE;
        if (totalSpent >= 10000) return Offer.BuyerCategory.PRIME;
        if (totalSpent >= 1000) return Offer.BuyerCategory.VERIFIED;
        return Offer.BuyerCategory.NOT_VERIFIED;
    }
}