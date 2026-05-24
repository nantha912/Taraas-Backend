package com.LocalService.lsp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    private String id;
    private String name;
    private String email;
    
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    
    private String profilePhotoUrl;
    private String city;
    private Double totalSpent;
    private String buyerCategory;
    private LocalDateTime createdAt;

    public CustomerResponse(String id, String name, String email, String profilePhotoUrl, String city, Double totalSpent, String buyerCategory, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.profilePhotoUrl = profilePhotoUrl;
        this.city = city;
        this.totalSpent = totalSpent;
        this.buyerCategory = buyerCategory;
        this.createdAt = createdAt;
    }
}
