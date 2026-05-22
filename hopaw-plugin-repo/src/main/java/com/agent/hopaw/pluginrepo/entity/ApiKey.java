package com.agent.hopaw.pluginrepo.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ApiKey {

    private Long id;
    private Long userId;
    private String keyValue;
    private String name;
    private boolean locked;
    private String createdAt;
    private String updatedAt;
    private LocalDateTime expiresAt;

    private String username;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getExpiresAtStr() {
        if (expiresAt == null) return null;
        return expiresAt.format(DTF);
    }

    public boolean isExpired() {
        if (expiresAt == null) return false;
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isExpiringSoon() {
        if (expiresAt == null) return false;
        return !isExpired() && expiresAt.isBefore(LocalDateTime.now().plusDays(7));
    }
}