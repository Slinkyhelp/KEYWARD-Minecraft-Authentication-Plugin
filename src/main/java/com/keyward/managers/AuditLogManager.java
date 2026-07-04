package com.keyward.managers;

import com.keyward.Keyward;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

public class AuditLogManager {
    private final Keyward plugin;

    public AuditLogManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public void log(String accountId, String eventType, String actor, String ipHash, String metadataJson) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO audit_log (id, account_id, event_type, actor, ip_address, metadata, timestamp) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, accountId);
                statement.setString(3, eventType);
                statement.setString(4, actor);
                statement.setString(5, ipHash);
                statement.setString(6, metadataJson);
                statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to write audit log: " + e.getMessage());
            }
        });
    }

    public void logAttempt(String username, String ipHash, boolean success, String failureReason, String captchaType) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO login_attempts (id, username_attempted, ip_address, success, failure_reason, captcha_type_shown, timestamp) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, username);
                statement.setString(3, ipHash);
                statement.setInt(4, success ? 1 : 0);
                statement.setString(5, failureReason);
                statement.setString(6, captchaType);
                statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to write login attempt: " + e.getMessage());
            }
        });
    }
}