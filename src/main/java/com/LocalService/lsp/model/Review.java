package com.LocalService.lsp.model;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Getter
@Setter
@Data
@Document(collection = "reviews")
public class Review {

    @Id
    private String id;               // Required for savedReview.getId() in Controller

    private String providerId;
    private String providerName;     // Useful for displaying in Customer Profile
    private String customerId;       // Required for findByCustomerId in Repository
    private String customerName;
    private String text;
    private Integer rating;          // To support star ratings (1-5)

    private LocalDateTime createdAt; // Required for review.setCreatedAt() in Controller

    public Review() {}

    public Review(String id, String providerId, String providerName, String customerId, String customerName, String text, Integer rating, LocalDateTime createdAt) {
        this.id = id;
        this.providerId = providerId;
        this.providerName = providerName;
        this.customerId = customerId;
        this.customerName = customerName;
        this.text = text;
        this.rating = rating;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getText() {
        return text;
    }

    public Integer getRating() {
        return rating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}