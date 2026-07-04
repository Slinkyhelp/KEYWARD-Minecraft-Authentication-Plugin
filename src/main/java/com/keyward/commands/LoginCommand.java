package com.keyward.commands;

import com.keyward.Keyward;
import com.keyward.model.Account;
import com.keyward.util.HashUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {
    private final Keyward plugin;

    public LoginCommand(Keyward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;

        if (plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().msg("login.already-authenticated"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(plugin.getConfigManager().msg("login.usage"));
            return true;
        }
        String password = args[0];
        String key = player.getName().toLowerCase();

        if (plugin.getAuthManager().isLockedOut(key)) {
            player.sendMessage(plugin.getConfigManager().msg("login.locked-out",
                    "seconds", plugin.getAuthManager().remainingLockoutSeconds(key)));
            return true;
        }

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        String ipHash = HashUtil.sha256Hex(ip);

        plugin.getAuthManager().getAccount(player.getName()).thenAccept(existingOpt ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (existingOpt.isEmpty()) {
                        player.sendMessage(plugin.getConfigManager().msg("login.no-account"));
                        return;
                    }
                    Account account = existingOpt.get();
                    plugin.getAuthManager().verifyPassword(account, password).thenAccept(valid ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (Boolean.TRUE.equals(valid)) {
                                    plugin.getAuthManager().clearFailures(key);
                                    plugin.getAuthManager().setAuthenticated(player.getUniqueId(), true);
                                    plugin.getAuthManager().updateLastLogin(account.getId());
                                    plugin.getLimboManager().exitLimbo(player);
                                    String fingerprint = plugin.getSessionManager().computeFingerprint(
                                            player.getUniqueId().toString(), "unknown", player.getLocale());
                                    plugin.getSessionManager().createSession(account.getId(), ipHash, fingerprint);
                                    plugin.getAuditLogManager().log(account.getId(), "login", "player", ipHash, "{}");
                                    player.sendMessage(plugin.getConfigManager().msg("login.success"));
                                } else {
                                    plugin.getAuthManager().recordFailure(key);
                                    plugin.getAttackModeManager().recordFailure(ipHash);
                                    plugin.getAuditLogManager().log(account.getId(), "login_fail", "player", ipHash, "{}");
                                    player.sendMessage(plugin.getConfigManager().msg("login.wrong-password"));
                                }
                            }));
                }));
        return true;
    }
}