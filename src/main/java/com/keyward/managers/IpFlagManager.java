package com.keyward.managers;

import com.keyward.Keyward;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CompletableFuture;

public class IpFlagManager {
    private final Keyward plugin;

    public IpFlagManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> isFlagged(String ipHash) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT expires_at FROM flagged_ips WHERE ip_address = ?")) {
                statement.setString(1, ipHash);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        long expiresAt = rs.getLong("expires_at");
                        future.complete(expiresAt == 0 || expiresAt > System.currentTimeMillis());
                    } else {
                        future.complete(false);
                    }
                }
            } catch (Exception e) {
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Void> flag(String ipHash, String reason, long expiresAt, boolean auto) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT OR REPLACE INTO flagged_ips (ip_address, reason, flagged_at, expires_at, auto_generated) " +
                                 "VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, ipHash);
                statement.setString(2, reason);
                statement.setLong(3, System.currentTimeMillis());
                statement.setLong(4, expiresAt);
                statement.setInt(5, auto ? 1 : 0);
                statement.executeUpdate();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> unflag(String ipHash) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM flagged_ips WHERE ip_address = ?")) {
                statement.setString(1, ipHash);
                statement.executeUpdate();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}