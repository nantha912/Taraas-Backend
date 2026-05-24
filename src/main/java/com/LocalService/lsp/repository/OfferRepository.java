package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Offer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OfferRepository extends MongoRepository<Offer, String> {
    List<Offer> findByProviderId(String providerId);
    List<Offer> findByProviderIdAndIsActiveTrue(String providerId);
    List<Offer> findByIsActiveTrue();
}