package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "onboarding_trackers")
public class OnboardingTracker {

    @Id
    private String id; // This acts as our temporary trackerId token
    private Map<String, Object> providerFormData; // Captures the exact JSON state from React
    private String razorpayOrderId;
    private String status; // PENDING_PAYMENT, COMPLETED, EXPIRED
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Map<String, Object> getProviderFormData() { return providerFormData; }
    public void setProviderFormData(Map<String, Object> providerFormData) { this.providerFormData = providerFormData; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
