package com.LocalService.lsp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "profile_views")
public class ProfileView {
    @Id
    private String id;
    private String providerId;
    private String sessionId; // For deduplication
    private LocalDateTime timestamp;

    public ProfileView() {}
    public ProfileView(String providerId, String sessionId) {
        this.providerId = providerId;
        this.sessionId = sessionId;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getProviderId() { return providerId; }
    public String getSessionId() { return sessionId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setId(String id) { this.id = id; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

}