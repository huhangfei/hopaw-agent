package com.agent.hopaw.pluginrepo.service;

import com.agent.hopaw.pluginrepo.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));
        u.setDisplayName(rs.getString("display_name"));
        u.setRole(rs.getString("role"));
        u.setLocked(rs.getInt("locked") != 0);
        u.setCreatedAt(rs.getString("created_at"));
        u.setUpdatedAt(rs.getString("updated_at"));
        return u;
    };

    public UserService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public User findByUsername(String username) {
        List<User> users = jdbc.query("SELECT * FROM users WHERE username = ?", userRowMapper, username);
        return users.isEmpty() ? null : users.get(0);
    }

    public User findByEmail(String email) {
        List<User> users = jdbc.query("SELECT * FROM users WHERE email = ?", userRowMapper, email);
        return users.isEmpty() ? null : users.get(0);
    }

    public User findById(Long id) {
        List<User> users = jdbc.query("SELECT * FROM users WHERE id = ?", userRowMapper, id);
        return users.isEmpty() ? null : users.get(0);
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY created_at DESC", userRowMapper);
    }

    public User create(String username, String password, String displayName, String role) {
        String encoded = passwordEncoder.encode(password);
        jdbc.update("INSERT INTO users (username, password, displayName, role) VALUES (?, ?, ?, ?)",
                username, encoded, displayName, role);
        return findByUsername(username);
    }

    public User create(String username, String email, String password, String displayName, String role) {
        String encoded = passwordEncoder.encode(password);
        jdbc.update("INSERT INTO users (username, email, password, display_name, role) VALUES (?, ?, ?, ?, ?)",
                username, email, encoded, displayName, role);
        return findByUsername(username);
    }

    public User update(Long id, String displayName, String role, String password) {
        if (password != null && !password.isEmpty()) {
            String encoded = passwordEncoder.encode(password);
            jdbc.update("UPDATE users SET display_name = ?, role = ?, password = ?, " +
                            "updated_at = datetime('now','localtime') WHERE id = ?",
                    displayName, role, encoded, id);
        } else {
            jdbc.update("UPDATE users SET display_name = ?, role = ?, " +
                            "updated_at = datetime('now','localtime') WHERE id = ?",
                    displayName, role, id);
        }
        return findById(id);
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM api_keys WHERE user_id = ?", id);
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    public void setLocked(Long id, boolean locked) {
        jdbc.update("UPDATE users SET locked = ?, updated_at = datetime('now','localtime') WHERE id = ?",
                locked ? 1 : 0, id);
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public User updatePassword(Long userId, String newPassword) {
        String encoded = passwordEncoder.encode(newPassword);
        jdbc.update("UPDATE users SET password = ?, updated_at = datetime('now','localtime') WHERE id = ?",
                encoded, userId);
        return findById(userId);
    }
}