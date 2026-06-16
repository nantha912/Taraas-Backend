package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "system_settings")
public class SystemSettings {

    @Id
    private String id = "subscription_config"; // Static ID ensures only one global config exists
    private Double baseAmount;
    private Double discountedAmount;
    private String offerName;
    private String currency = "INR";
    private boolean active = true;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Double getBaseAmount() { return baseAmount; }
    public void setBaseAmount(Double baseAmount) { this.baseAmount = baseAmount; }
    public Double getDiscountedAmount() { return discountedAmount; }
    public void setDiscountedAmount(Double discountedAmount) { this.discountedAmount = discountedAmount; }
    public String getOfferName() { return offerName; }
    public void setOfferName(String offerName) { this.offerName = offerName; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}