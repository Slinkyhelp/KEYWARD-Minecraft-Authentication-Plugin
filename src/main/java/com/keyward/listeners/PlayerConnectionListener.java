package com.keyward.listeners;

import com.keyward.Keyward;
import com.keyward.model.Account;
import com.keyward.util.HashUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerConnectionListener implements Listener {
    private static final List<String> ALLOWED_COMMANDS = Arrays.asList("/login", "/register", "/2fa");

    private final Keyward plugin;

    public PlayerConnectionListener(Keyward plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        String ipHash = HashUtil.sha256Hex(ip);

        plugin.getIpFlagManager().isFlagged(ipHash).thenAccept(flagged -> {
            if (flagged) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.kickPlayer(plugin.getConfigManager().msg("login.flagged-ip")));
                return;
            }
            handleAuthFlow(player, ipHash);
        });
    }

    private void handleAuthFlow(Player player, String ipHash) {
        boolean autoVerify = plugin.getConfigManager().premiumAutoVerify();
        String username = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<UUID> premiumUuid = autoVerify ? plugin.getMojangAPI().checkPremium(username) : Optional.empty();
            plugin.getAuthManager().getAccount(username).thenAccept(existing ->
                    Bukkit.getScheduler().runTask(plugin, () -> resolveAccount(player, ipHash, existing, premiumUuid)));
        });
    }

    private void resolveAccount(Player player, String ipHash, Optional<Account> existing, Optional<UUID> premiumUuid) {
        if (!player.isOnline()) {
            return;
        }

        if (premiumUuid.isPresent() && (existing.isEmpty() || "PREMIUM".equals(existing.get().getAccountType()))) {
            if (existing.isPresent()) {
                autoAuthenticate(player, existing.get(), ipHash);
            } else {
                plugin.getAuthManager().registerPremium(player.getName(), premiumUuid.get())
                        .thenAccept(account -> Bukkit.getScheduler().runTask(plugin,
                                () -> autoAuthenticate(player, account, ipHash)));
            }
            return;
        }

        if (existing.isPresent()) {
            String fingerprint = plugin.getSessionManager().computeFingerprint(
                    player.getUniqueId().toString(), "unknown", player.getLocale());
            plugin.getSessionManager().hasValidSession(existing.get().getId(), ipHash, fingerprint)
                    .thenAccept(valid -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (valid) {
                            autoAuthenticate(player, existing.get(), ipHash);
                        } else {
                            plugin.getLimboManager().enterLimbo(player);
                            player.sendMessage(plugin.getConfigManager().msg("limbo.prompt-login"));
                        }
                    }));
        } else {
            plugin.getLimboManager().enterLimbo(player);
            player.sendMessage(plugin.getConfigManager().msg("limbo.prompt-register"));
        }
    }

    private void autoAuthenticate(Player player, Account account, String ipHash) {
        plugin.getAuthManager().setAuthenticated(player.getUniqueId(), true);
        plugin.getAuthManager().updateLastLogin(account.getId());
        String fingerprint = plugin.getSessionManager().computeFingerprint(
                player.getUniqueId().toString(), "unknown", player.getLocale());
        plugin.getSessionManager().createSession(account.getId(), ipHash, fingerprint);
        plugin.getAuditLogManager().log(account.getId(), "login", "system", ipHash, "{\"auto\":true}");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getLimboManager().exitLimbo(player);
        plugin.getAuthManager().logout(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().msg("limbo.blocked-action"));
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            return;
        }
        String command = event.getMessage().split(" ")[0];
        boolean allowed = ALLOWED_COMMANDS.stream().anyMatch(command::equalsIgnoreCase);
        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().msg("limbo.blocked-action"));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (!plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}