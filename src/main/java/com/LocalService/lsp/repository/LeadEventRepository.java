package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.LeadEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LeadEventRepository
 * Handles persistence and range-based retrieval of intent-based contact events.
 */
@Repository
public interface LeadEventRepository extends MongoRepository<LeadEvent, String> {

    /**
     * Fetches all contact attempts (Phone, WhatsApp, Email) for a provider within a date range.
     * This provides the "Leads Count" and "Lead Activity" timeline in the Insights dashboard.
     * * @param providerId The ID of the professional.
     * @param start The beginning of the filtered month.
     * @param end The end of the filtered month.
     * @return List of LeadEvent records.
     */
    List<LeadEvent> findAllByProviderIdAndTimestampBetween(String providerId, LocalDateTime start, LocalDateTime end);
}