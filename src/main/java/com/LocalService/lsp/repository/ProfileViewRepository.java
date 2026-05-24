package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.ProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ProfileViewRepository
 * Handles persistence and range-based retrieval of profile visit logs.
 */
@Repository
public interface ProfileViewRepository extends MongoRepository<ProfileView, String> {

    /**
     * Fetches all profile view records for a specific provider within a date range.
     * Used by the InsightsController to calculate unique session views.
     * * @param providerId The ID of the professional.
     * @param start The beginning of the billing/insight month.
     * @param end The end of the billing/insight month.
     * @return List of ProfileView events.
     */
    List<ProfileView> findAllByProviderIdAndTimestampBetween(String providerId, LocalDateTime start, LocalDateTime end);
}