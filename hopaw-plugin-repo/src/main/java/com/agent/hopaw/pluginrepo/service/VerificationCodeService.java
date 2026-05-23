package com.agent.hopaw.pluginrepo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class VerificationCodeService {

    private final JdbcTemplate jdbc;
    private final Random random = new Random();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public VerificationCodeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String generateCode(String email, String type, int expireMinutes) {
        // 生成 6 位数字验证码
        String code = String.format("%06d", random.nextInt(1000000));
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(expireMinutes);
        
        jdbc.update("INSERT INTO verification_codes (email, code, type, expires_at) VALUES (?, ?, ?, ?)",
                email, code, type, expiresAt.format(DATE_TIME_FORMATTER));
        
        return code;
    }

    public boolean verifyCode(String email, String code, String type) {
        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        
        List<Map<String, Object>> results = jdbc.queryForList(
                "SELECT id FROM verification_codes WHERE email = ? AND code = ? AND type = ? AND used = 0 AND expires_at > ?",
                email, code, type, now);
        
        if (!results.isEmpty()) {
            Number idNumber = (Number) results.get(0).get("id");
            Long id = idNumber.longValue();
            jdbc.update("UPDATE verification_codes SET used = 1 WHERE id = ?", id);
            return true;
        }
        return false;
    }

    public void cleanupExpired() {
        jdbc.update("DELETE FROM verification_codes WHERE expires_at < datetime('now','localtime')");
    }
}
