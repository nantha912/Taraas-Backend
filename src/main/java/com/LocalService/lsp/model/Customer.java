package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "customers")
public class Customer {

    @Id
    private String id;

    private String name;
    private String email;
    private String password;
    private String profilePhotoUrl;
    private String city;
    private double totalSpent;

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public Double getTotalSpent() {
        return totalSpent;
    }

    /* ==============================
           🔹 BUYER CATEGORY (NEW)
           ============================== */
    private BuyerCategory buyerCategory = BuyerCategory.NOT_VERIFIED;

    private LocalDateTime createdAt = LocalDateTime.now();

    /* ==============================
       🔹 BUYER CATEGORY ENUM
       ============================== */
    public enum BuyerCategory {
        NOT_VERIFIED(0),
        VERIFIED(1),
        PRIME(2),
        ELITE(3);

        private final int rank;

        BuyerCategory(int rank) {
            this.rank = rank;
        }

        public int getRank() {
            return rank;
        }
    }

    public Customer() {}

    /* ==============================
       🔹 GETTERS & SETTERS
       ============================== */

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public BuyerCategory getBuyerCategory() {
        return buyerCategory;
    }

    public void setBuyerCategory(BuyerCategory buyerCategory) {
        this.buyerCategory = buyerCategory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
