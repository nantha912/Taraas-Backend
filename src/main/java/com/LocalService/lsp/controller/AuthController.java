package com.LocalService.lsp.controller;

import com.LocalService.lsp.dto.AuthResponse;
import com.LocalService.lsp.dto.GoogleLoginRequest;
import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.LoginRequest;
import com.LocalService.lsp.model.Offer;
import com.LocalService.lsp.model.Transaction;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.TransactionRepository;
import com.LocalService.lsp.security.JwtTokenProvider;
import com.LocalService.lsp.service.GoogleAuthService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Login endpoint - validates credentials and returns JWT token with customer details
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login request is required"));
        }
        
        String rawEmail = loginRequest.getEmail();
        String password = loginRequest.getPassword();
        
        if (rawEmail == null || rawEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }

        try {
            // Trim email to handle whitespace
            String email = rawEmail.trim().toLowerCase();
            
            // Find customer by email
            Customer customer = customerRepository.findByEmail(email)
                    .orElseThrow(() -> new BadCredentialsException("Customer not found with email: " + email));

            // Validate password
            if (!passwordEncoder.matches(password, customer.getPassword())) {
                throw new BadCredentialsException("Invalid password");
            }

            // Generate tokens
            String token = tokenProvider.generateToken(customer.getEmail());
            String refreshToken = tokenProvider.generateRefreshToken(customer.getEmail());

            // Calculate buyer category and total spent
            double totalSpent = calculateTotalSpent(customer.getId());
            Offer.BuyerCategory buyerCategory = calculateBuyerCategory(totalSpent);

            // Build response with all customer details
            AuthResponse response = new AuthResponse(token, refreshToken, 
                    Optional.ofNullable(customer.getName()).orElse("Unknown"), 
                    customer.getEmail());
            
            response.setId(customer.getId());
            response.setProfilePhotoUrl(Optional.ofNullable(customer.getProfilePhotoUrl()).orElse(""));
            response.setBuyerCategory(buyerCategory != null ? buyerCategory.name() : "NOT_VERIFIED");
            response.setTotalSpent(totalSpent);

            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    /**
     * Refresh token endpoint - generates new access token from refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String refreshTokenHeader) {
        if (refreshTokenHeader == null || refreshTokenHeader.isBlank()) {
            return ResponseEntity.badRequest().body("Authorization header is required");
        }
        try {
            String refreshToken = extractTokenFromHeader(refreshTokenHeader);

            if (!tokenProvider.validateToken(refreshToken)) {
                return ResponseEntity.badRequest().body("Invalid or expired refresh token");
            }

            String username = tokenProvider.getUsernameFromToken(refreshToken);
            String newToken = tokenProvider.generateToken(username);

            return ResponseEntity.ok(new AuthResponse(newToken, refreshToken, username, username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to refresh token: " + e.getMessage());
        }
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromHeader(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header format");
    }

    /**
     * Calculate buyer category based on total spent in last 12 months
     */
    private Offer.BuyerCategory calculateBuyerCategory(double totalSpent) {
        if (totalSpent >= 100000) return Offer.BuyerCategory.ELITE;
        if (totalSpent >= 10000) return Offer.BuyerCategory.PRIME;
        if (totalSpent >= 1000) return Offer.BuyerCategory.VERIFIED;
        return Offer.BuyerCategory.NOT_VERIFIED;
    }

    /**
     * Calculate total spent in last 12 months from completed transactions
     */
    private double calculateTotalSpent(String customerId) {
        if (customerId == null) return 0.0;
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        List<Transaction> completed = transactionRepository.findByCustomerIdAndStatusAndCreatedAtAfter(
                customerId,
                "COMPLETED",
                twelveMonthsAgo
        );
        if (completed == null) return 0.0;
        return completed.stream()
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount() : 0.0)
                .sum();
    }
    
    @Autowired
    private GoogleAuthService googleAuthService;

    @Value("${app.frontend.redirect-url:http://localhost:5173}")
    private String frontendRedirectUrl;

    @PostMapping(value = "/google", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            public ResponseEntity<?> loginWithGoogleRedirect(
                    @RequestParam("credential") String credential,
                    @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                    @CookieValue(value = "redirect_uri", required = false) String redirectUriCookie
            ) {
                String destination = redirectUri;
                if (destination == null || destination.isBlank()) {
                    destination = redirectUriCookie;
                }
                if (destination == null || destination.isBlank()) {
                    destination = frontendRedirectUrl;
                }

                logger.info("Google redirect POST received. redirect_uri=[{}], credential-present=[{}]", redirectUri, credential != null && !credential.isBlank());

                if (credential == null || credential.isBlank()) {
                    logger.warn("Missing credential in Google redirect POST. Redirecting to destination with status missing_token: {}", destination);
                    return ResponseEntity.status(HttpStatus.SEE_OTHER)
                        .location(URI.create(destination + "?loginStatus=missing_token"))
                        .build();
                }

                try {
                    Map<String, Object> authResponse = googleAuthService.authenticateGoogleUser(credential);

                    String redirectUrl = UriComponentsBuilder.fromUriString(destination)
                        .queryParam("token", authResponse.get("token"))
                        .queryParam("refreshToken", authResponse.get("refreshToken"))
                        .queryParam("email", authResponse.get("email"))
                        .queryParam("name", authResponse.get("name"))
                        .queryParam("id", authResponse.get("id"))
                        .queryParam("role", authResponse.getOrDefault("role", "CUSTOMER"))
                        .queryParam("profilePhotoUrl", authResponse.getOrDefault("profilePhotoUrl", ""))
                        .encode()                                   // ← added: percent-encode every value
                        .build()
                        .toUriString();

                    logger.info("Google credential validated. Redirecting browser back to app: {}", redirectUrl);

                    return ResponseEntity.status(HttpStatus.SEE_OTHER)
                        .header(HttpHeaders.LOCATION, redirectUrl)
                        .build();

                } catch (Exception e) {
                    logger.error("Google authentication failed: {}", e.getMessage(), e);
                    String failureRedirect = UriComponentsBuilder.fromUriString(destination)   // ← was raw string concat
                        .queryParam("loginStatus", "failed")
                        .queryParam("reason", e.getMessage() != null ? e.getMessage() : "unknown_error")
                        .encode()
                        .build()
                        .toUriString();
                    return ResponseEntity.status(HttpStatus.SEE_OTHER)
                        .location(URI.create(failureRedirect))
                        .build();
                }
            }

    /**
     * Fallback GET handler so if Google or a browser lands on the endpoint via GET
     * we gracefully redirect back to the frontend instead of showing an API page.
     * This won't complete authentication (no credential was posted), but avoids
     * leaving the user stuck on the API URL.
     */
        @GetMapping("/google")
            public ResponseEntity<?> loginWithGoogleGet(
                @RequestParam(value = "credential", required = false) String credential,
                @RequestParam(value = "id_token", required = false) String idToken,
                @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                @CookieValue(value = "redirect_uri", required = false) String redirectUriCookie
            ) {
                String destination = redirectUri;
                if (destination == null || destination.isBlank()) {
                    destination = redirectUriCookie;
                }
                if (destination == null || destination.isBlank()) {
                    destination = frontendRedirectUrl;
                }

                String tokenCandidate = credential != null && !credential.isBlank() ? credential : idToken;
                if (tokenCandidate != null && !tokenCandidate.isBlank()) {
                    try {
                    Map<String, Object> authResponse = googleAuthService.authenticateGoogleUser(tokenCandidate);
                    String redirectUrl = UriComponentsBuilder.fromUriString(destination)
                        .queryParam("token", authResponse.get("token"))
                        .queryParam("refreshToken", authResponse.get("refreshToken"))
                        .queryParam("email", authResponse.get("email"))
                        .queryParam("name", authResponse.get("name"))
                        .queryParam("id", authResponse.get("id"))
                        .queryParam("role", authResponse.getOrDefault("role", "CUSTOMER"))
                        .queryParam("profilePhotoUrl", authResponse.getOrDefault("profilePhotoUrl", ""))
                        .encode()
                        .build()
                        .toUriString();

                    logger.info("Google credential (GET) validated. Redirecting browser back to app: {}", redirectUrl);
                    return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, redirectUrl)
                        .build();
                    } catch (Exception e) {
                    logger.error("Google authentication failed (GET): {}", e.getMessage(), e);
                    String failureRedirect = UriComponentsBuilder.fromUriString(destination)
                        .queryParam("loginStatus", "failed")
                        .queryParam("reason", e.getMessage() != null ? e.getMessage() : "unknown_error")
                        .encode()
                        .build()
                        .toUriString();
                    return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(failureRedirect))
                        .build();
                    }
                }

                logger.info("Google GET landed on /google without credential. Redirecting browser to frontend: {}", destination);
                return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(destination + "?loginStatus=method_not_supported"))
                    .build();
            }
}
