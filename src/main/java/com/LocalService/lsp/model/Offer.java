package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document(collection = "offers")
public class Offer {
    @Id
    private String id;

    private String providerId;
    private String title;
    private String description;
    private OfferType type;          // PERCENTAGE, FLAT, BUY_X_GET_Y, CONDITIONAL, CUSTOM
    private String value;            // e.g., "10", "500", "Buy 2 Get 1"
    private Map<String, Object> conditions; // For CONDITIONAL types (e.g., minOrderValue)
    private BuyerCategory minCategory; //

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean isActive = true;
    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean featured = false;     // highlighted offer
    private List<String> serviceCategory = new ArrayList<>();        // Plumbing, Gardening, etc
    private String location;
    private String providerName;
    private String providerProfilePhoto;
    private String serviceDeliveryType = "LOCAL";


    public enum OfferType {
        PERCENTAGE, FLAT, BUY_X_GET_Y, CONDITIONAL, CUSTOM
    }

    public enum BuyerCategory {
        NOT_VERIFIED(0),
        VERIFIED(1),
        PRIME(2),
        ELITE(3);

        private final int rank;
        BuyerCategory(int rank) { this.rank = rank; }
        public int getRank() { return rank; }
    }

    public Offer() {}

    // Getters and Setters

    public String getServiceDeliveryType() {return serviceDeliveryType;}

    public void setServiceDeliveryType(String serviceDeliveryType) {this.serviceDeliveryType = serviceDeliveryType;}

    public String getProviderName() {return providerName;}
    public void setProviderName(String providerName) {this.providerName = providerName;}
    public void setProviderProfilePhoto(String providerProfilePhoto) {this.providerProfilePhoto = providerProfilePhoto;}

    public String getProviderProfilePhoto() {return providerProfilePhoto;}

    public boolean isFeatured() {return featured;}
    public List<String> getServiceCategory() { return serviceCategory; }
    public String getLocation() {return location;}

    public void setFeatured(boolean featured) {this.featured = featured;}
    public void setServiceCategory(List<String> serviceCategory) { this.serviceCategory = serviceCategory; }
    public void setLocation(String location) {this.location = location;}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OfferType getType() { return type; }
    public void setType(OfferType type) { this.type = type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Map<String, Object> getConditions() { return conditions; }
    public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }
    public BuyerCategory getMinCategory() { return minCategory; }
    public void setMinCategory(BuyerCategory minCategory) { this.minCategory = minCategory; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}