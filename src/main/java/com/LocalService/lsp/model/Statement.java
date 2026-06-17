package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "billing")
public class Statement {
    @Id
    private String id;
    private String providerId;
    private String billingMonth;
    private String billingYear;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private Double confirmedTotal;    // Sum of COMPLETED transactions
    private Double commissionPercentage; // Default 5.0
    private Double commissionAmount;  // confirmedTotal * (percentage/100)

    private String status;            // "UNPAID", "PAID", "WAIVED"
    private LocalDateTime generatedAt;
    private String generatedBy;       // "SYSTEM" or "ADMIN"
    private LocalDateTime paidAt;
    private String razorpay_payment_id;

    public Statement() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getBillingMonth() { return billingMonth; }
    public void setBillingMonth(String billingMonth) { this.billingMonth = billingMonth; }
    public LocalDateTime getBillingStartDate() { return startDate; }
    public void setBillingStartDate(LocalDateTime billingStartDate) { this.startDate = billingStartDate; }
    public LocalDateTime getBillingEndDate() { return endDate; }
    public void setBillingEndDate(LocalDateTime billingEndDate) { this.endDate = billingEndDate; }
    public Double getConfirmedTotal() { return confirmedTotal; }
    public void setConfirmedTotal(Double confirmedTotal) { this.confirmedTotal = confirmedTotal; }
    public Double getCommissionPercentage() { return commissionPercentage; }
    public void setCommissionPercentage(Double commissionPercentage) { this.commissionPercentage = commissionPercentage; }
    public Double getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(Double commissionAmount) { this.commissionAmount = commissionAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public String getBillingYear() {return billingYear;}

    public void setBillingYear(String billingYear) {this.billingYear = billingYear;}

    public String getRazorpayPaymentId() {
    return razorpay_payment_id;
}
    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpay_payment_id = razorpayPaymentId;
    }
}