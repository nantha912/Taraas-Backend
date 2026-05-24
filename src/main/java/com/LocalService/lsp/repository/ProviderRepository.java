package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Provider;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends MongoRepository<Provider, String> {

    List<Provider> findByServiceCategoryContainingIgnoreCase(String serviceCategory);

    List<Provider> findByLocationContainingIgnoreCase(String location);

    List<Provider> findByServiceCategoryContainingIgnoreCaseAndLocationContainingIgnoreCase(String serviceCategory, String location);

    // Removed findByWorkType as per the new serviceDeliveryType architecture.

    Optional<Provider> findByCustomerId(String customerId);
}