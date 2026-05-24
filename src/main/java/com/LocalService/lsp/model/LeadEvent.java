package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * LeadEvent Model - Updated
 * 1. Default Timestamp: Ensures the field is never null when saved via @RequestBody.
 * 2. Field Initialization: Sets timestamp at the moment of object creation.
 */
@Document(collection = "lead_events")
public class LeadEvent {
    @Id
    private String id;
    private String providerId;
    private String customerId;
    private String customerName;
    private String contactMethod;

    // Initialize with current time to prevent empty range query results
    private LocalDateTime timestamp = LocalDateTime.now();

    public LeadEvent() {}

    public LeadEvent(String providerId, String customerId, String customerName, String contactMethod) {
        this.providerId = providerId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.contactMethod = contactMethod;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getContactMethod() { return contactMethod; }
    public void setContactMethod(String contactMethod) { this.contactMethod = contactMethod; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}