package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.Provider;
import com.LocalService.lsp.model.Review;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.ReviewRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
public class ReviewController {

    private static final Logger logger = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Fetch all reviews for a specific provider
     * JOINS Review data with Customer Profile Photos
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getReviewsByProvider(
            @PathVariable String providerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        
        //logger.info("Fetching paginated reviews for providerId: {}, page: {}, size: {}", providerId, page, size);
        
        // 👑 Sort by newest reviews first
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        List<Review> records = reviewRepository.findByProviderId(providerId, pageable);
        return ResponseEntity.ok(records);
    }

    /**
     * Fetch all reviews written by a specific customer
     * JOINS Review data with Provider Profile Photos
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getReviewsByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }
        
        //logger.info("Fetching paginated reviews for customerId: {}, page: {}, size: {}", customerId, page, size);
        
        // 👑 Sort by newest reviews first
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        List<Review> records = reviewRepository.findByCustomerId(customerId, pageable);
        return ResponseEntity.ok(records);
    }

    @PostMapping
    public ResponseEntity<?> addReview(@RequestBody Review review) {
        if (review == null) {
            return ResponseEntity.badRequest().body("Review data is required");
        }

        @SuppressWarnings("unused")
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
        //logger.info("Review saved successfully. ID: {}", savedReview.getId());
        
        // 👑 O(1) INCREMENT METRICS
        updateProviderMetrics(savedReview.getProviderId(), savedReview.getRating(), null, "ADD");
        
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

        @SuppressWarnings("unused")
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

            // Keep track of old score to accurately compute differential variance
            int oldRating = existingReview.getRating();

            if (reviewDetails.getText() != null) existingReview.setText(reviewDetails.getText());
            if (newRating != null) existingReview.setRating(newRating);
            existingReview.setCreatedAt(LocalDateTime.now());
            Review updatedReview = reviewRepository.save(existingReview);
            
            // 👑 O(1) ROLLING UPDATE ADJUSTMENT
            if (newRating != null) {
                updateProviderMetrics(updatedReview.getProviderId(), updatedReview.getRating(), oldRating, "UPDATE");
            }
            
            return ResponseEntity.ok(updatedReview);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Review ID is required");
        }

        @SuppressWarnings("unused")
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

        //logger.info("Deleting Review ID: {}", id);
        return reviewRepository.findById(id).map(review -> {
            if (!review.getCustomerId().equals(currentCustomer.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only delete your own reviews."));
            }
            reviewRepository.delete(review);
            
            // 👑 O(1) SUBTRACTION CLEANUP
            updateProviderMetrics(review.getProviderId(), review.getRating(), null, "DELETE");
            
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

        @SuppressWarnings("unused")
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
        Review savedReview = reviewRepository.save(review);
        
        // 👑 O(1) METRICS UPDATE FOR LEGACY ENTRY POINT
        updateProviderMetrics(providerId, savedReview.getRating(), null, "ADD");
        
        return ResponseEntity.ok(savedReview);
    }

    /**
     * 👑 HIGH-SCALE INCREMENTAL MATH MATRIX
     * Processes calculations using rolling formula configurations.
     * Memory Footprint: O(1) - Never scans arrays.
     */
    private void updateProviderMetrics(String providerId, int targetRating, Integer optionalOldRating, String action) {
        if (providerId == null || providerId.isBlank()) return;

        try {
            // Step 1: Surgically query ONLY the specific fields needed from the Provider
            Query providerQuery = new Query(Criteria.where("_id").is(providerId));
            providerQuery.fields().include("reviewCount").include("averageRating");
            
            Document providerDoc = mongoTemplate.findOne(providerQuery, Document.class, "providers");
            if (providerDoc == null) return;

            // Handle missing fields or default states safely
            int currentCount = providerDoc.getInteger("reviewCount", 0);
            double currentAvg = providerDoc.containsKey("averageRating") ? 
                    ((Number) providerDoc.get("averageRating")).doubleValue() : 0.0;

            int nextCount = currentCount;
            double nextAvg = currentAvg;

            switch (action.toUpperCase()) {
                case "ADD":
                    nextCount = currentCount + 1;
                    nextAvg = ((currentAvg * currentCount) + targetRating) / nextCount;
                    break;

                case "UPDATE":
                    if (optionalOldRating != null && currentCount > 0) {
                        // Adjusting variance without altering index bounds
                        nextAvg = ((currentAvg * currentCount) - optionalOldRating + targetRating) / currentCount;
                    }
                    break;

                case "DELETE":
                    nextCount = Math.max(0, currentCount - 1);
                    if (nextCount > 0) {
                        nextAvg = ((currentAvg * currentCount) - targetRating) / nextCount;
                    } else {
                        nextAvg = 0.0;
                    }
                    break;
            }

            // Normalizes mathematical rounding anomalies cleanly to 1 decimal place
            nextAvg = Math.round(nextAvg * 10.0) / 10.0;

            // Step 2: Push updates down to database
            Update updateOp = new Update()
                    .set("reviewCount", nextCount)
                    .set("averageRating", nextAvg);

            mongoTemplate.updateFirst(providerQuery, updateOp, "providers");
            
            //logger.info("Incremental Production Engine Metric Sync -> ID: {}, Action: {}, New Count: {}, New Avg: {}", 
            //        providerId, action, nextCount, nextAvg);

        } catch (Exception e) {
            logger.error("Failed to run incremental score allocation for Provider ID {}: {}", providerId, e.getMessage());
        }
    }
}