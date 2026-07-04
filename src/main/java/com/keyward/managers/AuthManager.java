package com.keyward.managers;

import com.keyward.Keyward;
import com.keyward.model.Account;
import com.keyward.util.Argon2Util;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AuthManager {
    private final Keyward plugin;
    private final Argon2Util argon2;
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutUntil = new ConcurrentHashMap<>();

    public AuthManager(Keyward plugin) {
        this.plugin = plugin;
        this.argon2 = new Argon2Util(
                plugin.getConfigManager().argon2MemoryKb(),
                plugin.getConfigManager().argon2Iterations(),
                plugin.getConfigManager().argon2Parallelism());
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public void setAuthenticated(UUID uuid, boolean value) {
        if (value) {
            authenticated.add(uuid);
        } else {
            authenticated.remove(uuid);
        }
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private <T> CompletableFuture<T> supplyAsync(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Optional<Account>> getAccount(String username) {
        return supplyAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM accounts WHERE lower(username) = lower(?)")) {
                statement.setString(1, username);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapAccount(rs));
                    }
                }
            }
            return Optional.<Account>empty();
        });
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        return Account.builder()
                .id(rs.getString("id"))
                .username(rs.getString("username"))
                .premiumUuid(rs.getString("premium_uuid"))
                .passwordHash(rs.getString("password_hash"))
                .accountType(rs.getString("account_type"))
                .status(rs.getString("status"))
                .totpSecret(rs.getString("totp_secret"))
                .backupCodeHashes(rs.getString("backup_code_hashes"))
                .createdAt(rs.getLong("created_at"))
                .lastLoginAt(rs.getLong("last_login_at"))
                .fixedUniqueId(rs.getString("fixed_unique_id"))
                .build();
    }

    public CompletableFuture<Account> registerCracked(String username, String password) {
        return supplyAsync(() -> {
            String hash = argon2.hash(password);
            Account account = Account.builder()
                    .id(UUID.randomUUID().toString())
                    .username(username)
                    .premiumUuid(null)
                    .passwordHash(hash)
                    .accountType("CRACKED")
                    .status("ACTIVE")
                    .totpSecret(null)
                    .backupCodeHashes(null)
                    .createdAt(System.currentTimeMillis())
                    .lastLoginAt(System.currentTimeMillis())
                    .fixedUniqueId(UUID.randomUUID().toString())
                    .build();
            insertAccount(account);
            return account;
        });
    }

    public CompletableFuture<Account> registerPremium(String username, UUID premiumUuid) {
        return supplyAsync(() -> {
            Account account = Account.builder()
                    .id(UUID.randomUUID().toString())
                    .username(username)
                    .premiumUuid(premiumUuid.toString())
                    .passwordHash(null)
                    .accountType("PREMIUM")
                    .status("ACTIVE")
                    .totpSecret(null)
                    .backupCodeHashes(null)
                    .createdAt(System.currentTimeMillis())
                    .lastLoginAt(System.currentTimeMillis())
                    .fixedUniqueId(premiumUuid.toString())
                    .build();
            insertAccount(account);
            return account;
        });
    }

    private void insertAccount(Account account) throws SQLException {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO accounts (id, username, premium_uuid, password_hash, account_type, status, " +
                             "totp_secret, backup_code_hashes, created_at, last_login_at, fixed_unique_id) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, account.getId());
            statement.setString(2, account.getUsername());
            statement.setString(3, account.getPremiumUuid());
            statement.setString(4, account.getPasswordHash());
            statement.setString(5, account.getAccountType());
            statement.setString(6, account.getStatus());
            statement.setString(7, account.getTotpSecret());
            statement.setString(8, account.getBackupCodeHashes());
            statement.setLong(9, account.getCreatedAt());
            statement.setLong(10, account.getLastLoginAt());
            statement.setString(11, account.getFixedUniqueId());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Boolean> verifyPassword(Account account, String password) {
        return supplyAsync(() -> argon2.verify(password, account.getPasswordHash()));
    }

    public CompletableFuture<Void> updatePassword(String accountId, String newPassword) {
        return runAsync(() -> {
            String hash = argon2.hash(newPassword);
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE accounts SET password_hash = ? WHERE id = ?")) {
                statement.setString(1, hash);
                statement.setString(2, accountId);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updateLastLogin(String accountId) {
        return runAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE accounts SET last_login_at = ? WHERE id = ?")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, accountId);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> setTotpSecret(String accountId, String secret, String backupHashesCsv) {
        return runAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE accounts SET totp_secret = ?, backup_code_hashes = ? WHERE id = ?")) {
                statement.setString(1, secret);
                statement.setString(2, backupHashesCsv);
                statement.setString(3, accountId);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean isLockedOut(String key) {
        Long until = lockoutUntil.get(key);
        return until != null && System.currentTimeMillis() < until;
    }

    public long remainingLockoutSeconds(String key) {
        Long until = lockoutUntil.get(key);
        if (until == null) {
            return 0;
        }
        return Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }

    public void recordFailure(String key) {
        int attempts = failedAttempts.merge(key, 1, Integer::sum);
        int maxAttempts = plugin.getConfigManager().maxLoginAttempts();
        if (attempts >= maxAttempts) {
            int base = plugin.getConfigManager().baseCooldownSeconds();
            long cooldown = (long) (base * Math.pow(2, attempts - maxAttempts)) * 1000L;
            cooldown = Math.min(cooldown, 3600_000L);
            lockoutUntil.put(key, System.currentTimeMillis() + cooldown);
        }
    }

    public void clearFailures(String key) {
        failedAttempts.remove(key);
        lockoutUntil.remove(key);
    }

    public void logout(UUID uuid) {
        authenticated.remove(uuid);
    }
}