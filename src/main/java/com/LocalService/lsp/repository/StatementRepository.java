package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Statement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StatementRepository extends MongoRepository<Statement, String> {

    /**
     * Fetch all billing history for a specific provider.
     */
    List<Statement> findByProviderId(String providerId);

    /**
     * Idempotency Check: Checks if a statement already exists for a provider in a specific month.
     * Required for the 'calculateForMonth' service logic to avoid duplicate billing.
     * * @param providerId The unique ID of the provider.
     * @param billingMonth The month string in YYYY-MM format.
     * @return true if a record exists.
     */
    boolean existsByProviderIdAndBillingMonth(String providerId, String billingMonth);
}