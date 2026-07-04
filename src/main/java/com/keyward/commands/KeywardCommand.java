package com.keyward.commands;

import com.keyward.Keyward;
import com.keyward.util.HashUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KeywardCommand implements CommandExecutor {
    private final Keyward plugin;
    private final Map<UUID, Long> panicConfirmations = new ConcurrentHashMap<>();

    public KeywardCommand(Keyward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("keyward.admin")) {
            sender.sendMessage(plugin.getConfigManager().msg("keyward.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("\u00A7eUsage: /keyward <panic|verify|flagip|unflagip|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "panic":
                handlePanic(sender, args);
                break;
            case "verify":
                handleVerify(sender);
                break;
            case "flagip":
                handleFlag(sender, args);
                break;
            case "unflagip":
                handleUnflag(sender, args);
                break;
            case "reload":
                plugin.getConfigManager().reload();
                sender.sendMessage(plugin.getConfigManager().msg("keyward.reloaded"));
                break;
            default:
                sender.sendMessage("\u00A7eUsage: /keyward <panic|verify|flagip|unflagip|reload>");
        }
        return true;
    }

    private void handlePanic(CommandSender sender, String[] args) {
        UUID id = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);
        boolean confirmed = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        Long requestedAt = panicConfirmations.get(id);

        if (!confirmed || requestedAt == null || System.currentTimeMillis() - requestedAt > 30_000) {
            panicConfirmations.put(id, System.currentTimeMillis());
            sender.sendMessage(plugin.getConfigManager().msg("keyward.panic-confirm"));
            return;
        }

        panicConfirmations.remove(id);
        plugin.getSessionManager().revokeAllSessions();
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getAuthManager().logout(online.getUniqueId());
            online.kickPlayer("\u00A7cSecurity panic triggered. Please reconnect and re-authenticate.");
        }
        plugin.getAuditLogManager().log(null, "panic_triggered", sender.getName(), null, "{}");
        sender.sendMessage(plugin.getConfigManager().msg("keyward.panic-executed"));
    }

    private void handleVerify(CommandSender sender) {
        try {
            File jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            String hash = HashUtil.sha256File(jarFile);
            sender.sendMessage(plugin.getConfigManager().msg("keyward.verify-result", "hash", hash));
        } catch (URISyntaxException e) {
            sender.sendMessage("\u00A7cFailed to compute checksum: " + e.getMessage());
        }
    }

    private void handleFlag(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("\u00A7cUsage: /keyward flagip <ip> <reason>");
            return;
        }
        String ipHash = HashUtil.sha256Hex(args[1]);
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getIpFlagManager().flag(ipHash, reason, 0L, false);
        plugin.getAuditLogManager().log(null, "admin_action", sender.getName(), ipHash, "{\"action\":\"flag\"}");
        sender.sendMessage(plugin.getConfigManager().msg("keyward.flagged", "ip", args[1]));
    }

    private void handleUnflag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("\u00A7cUsage: /keyward unflagip <ip>");
            return;
        }
        String ipHash = HashUtil.sha256Hex(args[1]);
        plugin.getIpFlagManager().unflag(ipHash);
        plugin.getAuditLogManager().log(null, "admin_action", sender.getName(), ipHash, "{\"action\":\"unflag\"}");
        sender.sendMessage(plugin.getConfigManager().msg("keyward.unflagged", "ip", args[1]));
    }
}