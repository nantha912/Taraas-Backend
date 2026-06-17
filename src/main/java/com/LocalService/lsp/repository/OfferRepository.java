package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OfferRepository extends MongoRepository<Offer, String> {
    List<Offer> findByProviderId(String providerId);
    List<Offer> findByProviderIdAndIsActiveTrue(String providerId);
    
    // 👑 ULTRA-LIGHTWEIGHT NATIVE PAGINATION
    // Spring handles the array search and case-insensitivity mapping automatically
    Page<Offer> findByServiceCategoryIgnoreCase(String serviceCategory, Pageable pageable);
    // 👑 RELATIVE PARTIAL MATCH QUERY Engine
    // ?0 injects the category search term string.
    // $options: 'i' enables case-insensitivity globally across the array query scan.
    @Query("{ 'serviceCategory': { $regex: ?0, $options: 'i' } }")
    Page<Offer> findByServiceCategoryContainingRegex(String serviceCategory, Pageable pageable);
}