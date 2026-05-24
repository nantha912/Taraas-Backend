package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {

    /**
     * Find all reviews for a specific provider.
     * This is used to display feedback on the Provider Profile Page.
     */
    List<Review> findByProviderId(String providerId);

    /**
     * Find all reviews written by a specific customer.
     * This is used to populate the "My Reviews" tab on the Customer Profile Page.
     */
    List<Review> findByCustomerId(String customerId);
}