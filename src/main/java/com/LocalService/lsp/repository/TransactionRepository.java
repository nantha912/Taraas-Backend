package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TransactionRepository
 * Manages persistence for payments and supports range-based queries
 * for both billing and performance insights.
 */
@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    /**
     * Find all transactions associated with a provider.
     */
    List<Transaction> findByProviderId(String providerId);

    /**
     * Find all transactions associated with a customer.
     */
    List<Transaction> findByCustomerId(String customerId);

    /**
     * Fetch completed but unbilled transactions for general administration.
     */
    List<Transaction> findByStatusAndBilledFalse(String status);

    /**
     * INSIGHTS QUERY:
     * Fetches all transactions for a provider within a specific month regardless of billing status.
     * This powers the "Total Orders" and "Turnover" metrics in the Insights Dashboard.
     * * @param providerId The ID of the professional.
     * @param status The status (e.g., "COMPLETED").
     * @param start Beginning of the month.
     * @param end End of the month.
     * @return List of matching transactions.
     */
    List<Transaction> findAllByProviderIdAndStatusAndCreatedAtBetween(
            String providerId,
            String status,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * BILLING QUERY:
     * Fetches transactions that are COMPLETED, have NOT been billed yet, and fall within the range.
     * This powers the Monthly Commission Statement generation.
     * * @param providerId The ID of the professional.
     * @param status The status (e.g., "COMPLETED").
     * @param start Beginning of the billing cycle.
     * @param end End of the billing cycle.
     * @return List of transactions eligible for commission calculation.
     */
    List<Transaction> findByProviderIdAndStatusAndBilledFalseAndCreatedAtBetween(
            String providerId,
            String status,
            LocalDateTime start,
            LocalDateTime end
    );
    List<Transaction> findByCustomerIdAndStatusAndCreatedAtAfter(
            String customerId,
            String status,
            LocalDateTime createdAt
    );
}