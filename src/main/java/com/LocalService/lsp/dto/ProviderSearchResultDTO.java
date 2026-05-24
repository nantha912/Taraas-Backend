package com.LocalService.lsp.dto;

import com.LocalService.lsp.model.Provider;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Data Transfer Object for Aggregated Search Results.
 * Includes counts calculated on the database side.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderSearchResultDTO extends Provider {
    private long reviewCount = 0;
    private long completedOrders = 0;
    private double averageRating = 0.0;
    private double searchScore = 0.0; // Added to capture the final weighted score
}
