package com.keyward.managers;

import com.keyward.Keyward;
import com.keyward.util.HashUtil;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SessionManager {
    private final Keyward plugin;

    public SessionManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> hasValidSession(String accountId, String ipHash, String fingerprintHash) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long windowMs = plugin.getConfigManager().sessionTimeoutMinutes() * 60_000L;
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM sessions WHERE account_id = ? AND ip_address = ? AND device_fingerprint = ? " +
                                 "AND revoked = 0 AND created_at >= ? ORDER BY created_at DESC LIMIT 1")) {
                statement.setString(1, accountId);
                statement.setString(2, ipHash);
                statement.setString(3, fingerprintHash);
                statement.setLong(4, System.currentTimeMillis() - windowMs);
                try (ResultSet rs = statement.executeQuery()) {
                    future.complete(rs.next());
                }
            } catch (Exception e) {
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Void> createSession(String accountId, String ipHash, String fingerprintHash) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long timeoutMs = plugin.getConfigManager().sessionTimeoutMinutes() * 60_000L;
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO sessions (id, account_id, ip_address, device_fingerprint, created_at, expires_at, revoked) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, 0)")) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, accountId);
                statement.setString(3, ipHash);
                statement.setString(4, fingerprintHash);
                statement.setLong(5, System.currentTimeMillis());
                statement.setLong(6, System.currentTimeMillis() + timeoutMs);
                statement.executeUpdate();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> revokeAllSessions() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE sessions SET revoked = 1 WHERE revoked = 0")) {
                statement.executeUpdate();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public String computeFingerprint(String uuid, String clientBrand, String locale) {
        return HashUtil.sha256Hex(uuid + "|" + clientBrand + "|" + locale);
    }
}