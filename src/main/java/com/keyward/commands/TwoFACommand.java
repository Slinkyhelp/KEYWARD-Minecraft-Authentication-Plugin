package com.keyward.commands;

import com.keyward.Keyward;
import com.keyward.util.HashUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class TwoFACommand implements CommandExecutor {
    private final Keyward plugin;

    public TwoFACommand(Keyward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;

        if (!plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().msg("twofa.not-authenticated"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.getConfigManager().msg("twofa.usage"));
            return true;
        }

        plugin.getAuthManager().getAccount(player.getName()).thenAccept(existingOpt ->
                existingOpt.ifPresent(account -> Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (args[0].toLowerCase()) {
                        case "enable": {
                            String secret = plugin.getTotpManager().generateSecret();
                            List<String> backupCodes = plugin.getTotpManager().generateBackupCodes(8);
                            String hashesCsv = backupCodes.stream()
                                    .map(HashUtil::sha256Hex)
                                    .collect(Collectors.joining(","));
                            plugin.getAuthManager().setTotpSecret(account.getId(), secret, hashesCsv);
                            plugin.getAuditLogManager().log(account.getId(), "2fa_enabled", "player", null, "{}");
                            player.sendMessage(plugin.getConfigManager().msg("twofa.enabled",
                                    "secret", secret, "codes", String.join(", ", backupCodes)));
                            break;
                        }
                        case "disable": {
                            plugin.getAuthManager().setTotpSecret(account.getId(), null, null);
                            plugin.getAuditLogManager().log(account.getId(), "2fa_disabled", "player", null, "{}");
                            player.sendMessage(plugin.getConfigManager().msg("twofa.disabled"));
                            break;
                        }
                        case "verify": {
                            if (args.length != 2 || account.getTotpSecret() == null) {
                                player.sendMessage(plugin.getConfigManager().msg("twofa.invalid-code"));
                                return;
                            }
                            boolean valid = plugin.getTotpManager().verifyCode(account.getTotpSecret(), args[1]);
                            player.sendMessage(valid
                                    ? plugin.getConfigManager().msg("twofa.verified")
                                    : plugin.getConfigManager().msg("twofa.invalid-code"));
                            break;
                        }
                        default:
                            player.sendMessage(plugin.getConfigManager().msg("twofa.usage"));
                    }
                })));
        return true;
    }
}