package com.LocalService.lsp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {
    private String id;
    private String name;
    private List<String> serviceCategory;
    private String description;
    private Double price;
    private String workType;
    private String email;
    private String phoneNumber;
    private String whatsappNumber;
    private String location;
    private String city;
    private String serviceDeliveryType;
    private double[] coordinates;
    private LocalDateTime lastActive;
    private boolean profileComplete;
    private double responseRate;
    private String upiId;
    private String profilePhotoUrl;
    private List<String> portfolioPhotos;
    private String instagramLink;
    private String facebookLink;
    private String youtubeLink;
    private String twitterLink;
    private String websiteLink;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String customerId;
}
