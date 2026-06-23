package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "promoters")
public class Promoter {
    @Id
    private String id;
    private String name;
    
    @Indexed(unique = true)
    private String email;
    
    private List<SocialHandle> socialHandles; // 🟢 Allows multiple social handles
    
    @Indexed(unique = true)
    private String referralCode;
    private PayoutDetails payoutDetails;
    private LocalDateTime createdAt;

    public static class SocialHandle {
        private String platform; // INSTAGRAM, YOUTUBE, X, TIKTOK
        private String url;
        
        // Getters and Setters
        public String getPlatform() { return platform; }
        public void setPlatform(String p) { this.platform = p; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class PayoutDetails {
        private String upiId;
        private String bankAccount;
        
        // Getters and Setters
        public String getUpiId() { return upiId; }
        public void setUpiId(String upi) { this.upiId = upi; }
        public String getBankAccount() { return bankAccount; }
        public void setBankAccount(String acc) { this.bankAccount = acc; }
    }

    // Main Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
    public List<SocialHandle> getSocialHandles() { return socialHandles; }
    public void setSocialHandles(List<SocialHandle> sh) { this.socialHandles = sh; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String rc) { this.referralCode = rc; }
    public PayoutDetails getPayoutDetails() { return payoutDetails; }
    public void setPayoutDetails(PayoutDetails pd) { this.payoutDetails = pd; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime dt) { this.createdAt = dt; }
}