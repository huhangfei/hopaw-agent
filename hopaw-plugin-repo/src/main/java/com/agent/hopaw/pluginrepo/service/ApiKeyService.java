package com.agent.hopaw.pluginrepo.service;

import com.agent.hopaw.pluginrepo.entity.ApiKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
public class ApiKeyService {

    private final JdbcTemplate jdbc;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RowMapper<ApiKey> keyRowMapper = (rs, rowNum) -> {
        ApiKey k = new ApiKey();
        k.setId(rs.getLong("id"));
        k.setUserId(rs.getLong("user_id"));
        k.setKeyValue(rs.getString("key_value"));
        k.setName(rs.getString("name"));
        k.setLocked(rs.getInt("locked") != 0);
        k.setCreatedAt(rs.getString("created_at"));
        k.setUpdatedAt(rs.getString("updated_at"));
        String expiresAtStr = rs.getString("expires_at");
        if (expiresAtStr != null && !expiresAtStr.isEmpty()) {
            k.setExpiresAt(LocalDateTime.parse(expiresAtStr, DTF));
        }
        return k;
    };

    private final RowMapper<ApiKey> keyWithUserRowMapper = (rs, rowNum) -> {
        ApiKey k = keyRowMapper.mapRow(rs, rowNum);
        try { k.setUsername(rs.getString("username")); } catch (Exception ignored) {}
        return k;
    };

    public ApiKeyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ApiKey findByKeyValue(String keyValue) {
        List<ApiKey> keys = jdbc.query(
                "SELECT k.*, u.username FROM api_keys k LEFT JOIN users u ON k.user_id = u.id WHERE k.key_value = ?",
                keyWithUserRowMapper, keyValue);
        return keys.isEmpty() ? null : keys.get(0);
    }

    public List<ApiKey> findByUserId(Long userId) {
        return jdbc.query("SELECT * FROM api_keys WHERE user_id = ? ORDER BY created_at DESC",
                keyRowMapper, userId);
    }

    public List<ApiKey> findAll() {
        return jdbc.query(
                "SELECT k.*, u.username FROM api_keys k LEFT JOIN users u ON k.user_id = u.id ORDER BY k.created_at DESC",
                keyWithUserRowMapper);
    }

    public ApiKey create(Long userId, String name, LocalDateTime expiresAt) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String keyValue = "haw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        if (expiresAt != null) {
            jdbc.update("INSERT INTO api_keys (user_id, key_value, name, expires_at) VALUES (?, ?, ?, ?)",
                    userId, keyValue, name, expiresAt.format(DTF));
        } else {
            jdbc.update("INSERT INTO api_keys (user_id, key_value, name) VALUES (?, ?, ?)",
                    userId, keyValue, name);
        }
        return findByKeyValue(keyValue);
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM api_keys WHERE id = ?", id);
    }

    public void setLocked(Long id, boolean locked) {
        jdbc.update("UPDATE api_keys SET locked = ?, updated_at = datetime('now','localtime') WHERE id = ?",
                locked ? 1 : 0, id);
    }
}