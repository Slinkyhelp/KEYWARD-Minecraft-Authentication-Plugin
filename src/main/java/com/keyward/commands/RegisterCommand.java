package com.keyward.commands;

import com.keyward.Keyward;
import com.keyward.util.HashUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {
    private final Keyward plugin;

    public RegisterCommand(Keyward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;

        if (plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().msg("register.already-authenticated"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.getConfigManager().msg("register.usage"));
            return true;
        }
        String password = args[0];
        String confirm = args[1];

        if (!password.equals(confirm)) {
            player.sendMessage(plugin.getConfigManager().msg("register.password-mismatch"));
            return true;
        }
        int minLength = plugin.getConfigManager().minPasswordLength();
        if (password.length() < minLength) {
            player.sendMessage(plugin.getConfigManager().msg("register.password-too-short", "min", minLength));
            return true;
        }
        if (plugin.getBreachedPasswordList().isBreached(password)) {
            player.sendMessage(plugin.getConfigManager().msg("register.password-breached"));
            return true;
        }
        if (plugin.getAttackModeManager().isActive()) {
            player.sendMessage(plugin.getConfigManager().msg("register.attack-mode"));
            return true;
        }

        plugin.getAuthManager().getAccount(player.getName()).thenAccept(existing ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (existing.isPresent()) {
                        player.sendMessage(plugin.getConfigManager().msg("register.account-exists"));
                        return;
                    }
                    player.sendMessage(plugin.getConfigManager().msg("register.captcha-prompt"));
                    plugin.getCaptchaManager().startCaptcha(player,
                            () -> completeRegistration(player, password),
                            () -> player.sendMessage(plugin.getConfigManager().msg("register.captcha-failed")));
                }));
        return true;
    }

    private void completeRegistration(Player player, String password) {
        plugin.getAuthManager().registerCracked(player.getName(), password).thenAccept(account ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getAuthManager().setAuthenticated(player.getUniqueId(), true);
                    plugin.getLimboManager().exitLimbo(player);
                    String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
                    String ipHash = HashUtil.sha256Hex(ip);
                    String fingerprint = plugin.getSessionManager().computeFingerprint(
                            player.getUniqueId().toString(), "unknown", player.getLocale());
                    plugin.getSessionManager().createSession(account.getId(), ipHash, fingerprint);
                    plugin.getAuditLogManager().log(account.getId(), "register", "player", ipHash, "{}");
                    player.sendMessage(plugin.getConfigManager().msg("register.success"));
                }));
    }
}