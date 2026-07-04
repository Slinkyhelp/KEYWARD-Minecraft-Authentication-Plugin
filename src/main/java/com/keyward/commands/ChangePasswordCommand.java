package com.keyward.commands;

import com.keyward.Keyward;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChangePasswordCommand implements CommandExecutor {
    private final Keyward plugin;

    public ChangePasswordCommand(Keyward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;

        if (!plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().msg("changepassword.not-authenticated"));
            return true;
        }
        if (args.length != 3) {
            player.sendMessage(plugin.getConfigManager().msg("changepassword.usage"));
            return true;
        }
        String oldPassword = args[0];
        String newPassword = args[1];
        String confirm = args[2];

        if (!newPassword.equals(confirm)) {
            player.sendMessage(plugin.getConfigManager().msg("changepassword.mismatch"));
            return true;
        }

        plugin.getAuthManager().getAccount(player.getName()).thenAccept(existingOpt ->
                existingOpt.ifPresent(account -> plugin.getAuthManager().verifyPassword(account, oldPassword)
                        .thenAccept(valid -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (Boolean.TRUE.equals(valid)) {
                                plugin.getAuthManager().updatePassword(account.getId(), newPassword);
                                plugin.getAuditLogManager().log(account.getId(), "password_change", "player", null, "{}");
                                player.sendMessage(plugin.getConfigManager().msg("changepassword.success"));
                            } else {
                                player.sendMessage(plugin.getConfigManager().msg("changepassword.wrong-old"));
                            }
                        }))));
        return true;
    }
}