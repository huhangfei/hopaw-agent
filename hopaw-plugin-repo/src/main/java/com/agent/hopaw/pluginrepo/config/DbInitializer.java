package com.agent.hopaw.pluginrepo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DbInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DbInitializer.class);
    private final JdbcTemplate jdbc;

    public DbInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL UNIQUE, " +
                "password TEXT NOT NULL, " +
                "display_name TEXT, " +
                "role TEXT NOT NULL DEFAULT 'USER', " +
                "locked INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), " +
                "updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')))");

        jdbc.execute("CREATE TABLE IF NOT EXISTS api_keys (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "key_value TEXT NOT NULL UNIQUE, " +
                "name TEXT NOT NULL, " +
                "locked INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), " +
                "updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), " +
                "expires_at TEXT, " +
                "FOREIGN KEY (user_id) REFERENCES users(id))"); 


        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, "admin");
        if (count == null || count == 0) {
            String encodedPwd = new BCryptPasswordEncoder().encode("admin123");
            jdbc.update("INSERT INTO users (username, password, display_name, role) VALUES (?, ?, ?, ?)",
                    "admin", encodedPwd, "超级管理员", "ADMIN");
            log.info("Default admin user created (username: admin, password: admin123)");
        }

        log.info("Database initialized successfully");
    }
}