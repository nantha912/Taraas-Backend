package com.LocalService.lsp.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * Transaction Model
 * Includes necessary fields for JIT SSE Handshake:
 * 1. customerName/providerName: Required for attractive real-time alerts.
 * 2. status: Triggers front-end reactive windows (INITIATED, CUSTOMER_CONFIRMED, COMPLETED).
 * 3. billed: Supports admin accounting and repository queries.
 * 4. progress: Added to support visual progress bars in the customer profile page.
 */
@Document(collection = "transactions")
public class Transaction {
    @Id
    private String id;

    private String providerId;
    private String providerName;
    private String customerId;
    private String customerName;
    private Double amount;
    private String status;

    /**
     * progress (0-100): Used for the visual progress bar in the Customer Profile.
     * Derived from status or set manually for multi-step services.
     */
    private Integer progress = 0;
    private String rejectionReason;


    private LocalDateTime createdAt;
    private String transactionNote;

    // Required for findByStatusAndBilledFalse in repository
    private boolean billed = false;

    public Transaction() {}

    // Getters and Setters
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getTransactionNote() { return transactionNote; }
    public void setTransactionNote(String transactionNote) { this.transactionNote = transactionNote; }

    public boolean isBilled() { return billed; }
    public void setBilled(boolean billed) { this.billed = billed; }
}