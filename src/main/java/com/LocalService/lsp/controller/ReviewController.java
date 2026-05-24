package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.Review;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {

    private static final Logger logger = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Fetch all reviews for a specific provider
     * JOINS Review data with Customer Profile Photos
     */
    @GetMapping("/provider/{providerId}")
     public ResponseEntity<?> getReviewsByProvider(@PathVariable String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        logger.info("Fetching reviews for providerId: {}", providerId);
        return ResponseEntity.ok(reviewRepository.findByProviderId(providerId));
    }

    /**
     * Fetch all reviews written by a specific customer
     * JOINS Review data with Provider Profile Photos
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getReviewsByCustomer(@PathVariable String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }
        logger.info("Fetching reviews for customerId: {}", customerId);
        return ResponseEntity.ok(reviewRepository.findByCustomerId(customerId));
    }

    @PostMapping
    public ResponseEntity<?> addReview(@RequestBody Review review) {
        if (review == null) {
            return ResponseEntity.badRequest().body("Review data is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        Optional<Customer> customerOpt = customerRepository.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();
        
        // Ownership check first for security
        String customerIdInReview = review.getCustomerId();
        if (customerIdInReview != null && !currentCustomer.getId().equals(customerIdInReview)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only submit reviews as yourself."));
        }

        // Manual validation
        if (review.getProviderId() == null || review.getProviderId().isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        if (review.getProviderName() == null || review.getProviderName().isBlank()) {
            return ResponseEntity.badRequest().body("Provider name is required");
        }
        if (review.getCustomerName() == null || review.getCustomerName().isBlank()) {
            return ResponseEntity.badRequest().body("Customer name is required");
        }
        
        Integer rating = review.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
        }
        
        String text = review.getText();
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body("Review text cannot be empty");
        }

        if (review.getCreatedAt() == null) {
            review.setCreatedAt(LocalDateTime.now());
        }
        Review savedReview = reviewRepository.save(review);
        logger.info("Review saved successfully. ID: {}", savedReview.getId());
        return ResponseEntity.ok(savedReview);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReview(@PathVariable String id, @RequestBody Review reviewDetails) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Review ID is required");
        }
        if (reviewDetails == null) {
            return ResponseEntity.badRequest().body("Update data is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        Optional<Customer> customerOpt = customerRepository.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();

        return reviewRepository.findById(id).map(existingReview -> {
            // Ownership check first!
            if (!existingReview.getCustomerId().equals(currentCustomer.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only update your own reviews."));
            }
            
            // Manual validation after auth
            Integer newRating = reviewDetails.getRating();
            if (newRating != null && (newRating < 1 || newRating > 5)) {
                return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
            }

            // Prevent changing provider/customer IDs - return 403 for customerId change to match test expectations
            String newCustomerId = reviewDetails.getCustomerId();
            if (newCustomerId != null && !existingReview.getCustomerId().equals(newCustomerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Review owner (customerId) cannot be modified."));
            }
            
            String newProviderId = reviewDetails.getProviderId();
            if (newProviderId != null && !existingReview.getProviderId().equals(newProviderId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Review target (providerId) cannot be modified."));
            }

            if (reviewDetails.getText() != null) existingReview.setText(reviewDetails.getText());
            if (newRating != null) existingReview.setRating(newRating);
            existingReview.setCreatedAt(LocalDateTime.now());
            Review updatedReview = reviewRepository.save(existingReview);
            return ResponseEntity.ok(updatedReview);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Review ID is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        Optional<Customer> customerOpt = customerRepository.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();

        logger.info("Deleting Review ID: {}", id);
        return reviewRepository.findById(id).map(review -> {
            if (!review.getCustomerId().equals(currentCustomer.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only delete your own reviews."));
            }
            reviewRepository.delete(review);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/provider/{providerId}")
    public ResponseEntity<?> addReviewLegacy(@PathVariable String providerId, @RequestBody Review review) {
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        if (review == null) {
            return ResponseEntity.badRequest().body("Review data is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        Optional<Customer> customerOpt = customerRepository.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();
        if (review.getCustomerId() != null && !currentCustomer.getId().equals(review.getCustomerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only submit reviews as yourself."));
        }

        review.setProviderId(providerId);
        review.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(reviewRepository.save(review));
    }
}
