package com.keyward.managers;

import com.keyward.Keyward;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final Keyward plugin;
    private String jdbcUrl;

    public DatabaseManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        String fileName = plugin.getConfig().getString("database.file", "keyward.db");
        this.jdbcUrl = "jdbc:sqlite:" + new File(dataFolder, fileName).getAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite driver not found", e);
        }
        createTables();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void createTables() {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS accounts (" +
                        "id TEXT PRIMARY KEY, username TEXT UNIQUE, premium_uuid TEXT, password_hash TEXT, " +
                        "account_type TEXT, status TEXT, totp_secret TEXT, backup_code_hashes TEXT, " +
                        "created_at INTEGER, last_login_at INTEGER, fixed_unique_id TEXT)",
                "CREATE TABLE IF NOT EXISTS sessions (" +
                        "id TEXT PRIMARY KEY, account_id TEXT, ip_address TEXT, device_fingerprint TEXT, " +
                        "created_at INTEGER, expires_at INTEGER, revoked INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS login_attempts (" +
                        "id TEXT PRIMARY KEY, username_attempted TEXT, ip_address TEXT, success INTEGER, " +
                        "failure_reason TEXT, captcha_type_shown TEXT, timestamp INTEGER)",
                "CREATE TABLE IF NOT EXISTS audit_log (" +
                        "id TEXT PRIMARY KEY, account_id TEXT, event_type TEXT, actor TEXT, " +
                        "ip_address TEXT, metadata TEXT, timestamp INTEGER)",
                "CREATE TABLE IF NOT EXISTS flagged_ips (" +
                        "ip_address TEXT PRIMARY KEY, reason TEXT, flagged_at INTEGER, expires_at INTEGER, auto_generated INTEGER)"
        };
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    public void close() {
    }
}