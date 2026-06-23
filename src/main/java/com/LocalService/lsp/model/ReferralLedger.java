package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

@Document(collection = "referral_ledger")
public class ReferralLedger {
    @Id
    private String id;
    
    @Indexed
    private String promoterId;
    private String referralCode;
    
    @Indexed(unique = true)
    private String providerId;
    private String providerNameMasked;
    private double onboardingFeePaid;
    private double commissionEarned;
    private String status; // PENDING_VALIDATION, EARNED, SETTLED
    private LocalDateTime processedAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPromoterId() { return promoterId; }
    public void setPromoterId(String id) { this.promoterId = id; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String c) { this.referralCode = c; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String id) { this.providerId = id; }
    public String getProviderNameMasked() { return providerNameMasked; }
    public void setProviderNameMasked(String n) { this.providerNameMasked = n; }
    public double getOnboardingFeePaid() { return onboardingFeePaid; }
    public void setOnboardingFeePaid(double fee) { this.onboardingFeePaid = fee; }
    public double getCommissionEarned() { return commissionEarned; }
    public void setCommissionEarned(double comm) { this.commissionEarned = comm; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime dt) { this.processedAt = dt; }
}