package com.keyward.managers;

import com.keyward.Keyward;
import com.keyward.inventory.impl.CaptchaGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {
    private final Keyward plugin;
    private final Map<UUID, Runnable> pendingSuccess = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> pendingFailure = new ConcurrentHashMap<>();

    public CaptchaManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public void startCaptcha(Player player, Runnable onSuccess, Runnable onFailure) {
        pendingSuccess.put(player.getUniqueId(), onSuccess);
        pendingFailure.put(player.getUniqueId(), onFailure);
        CaptchaGUI gui = new CaptchaGUI(plugin, player);
        plugin.getGuiManager().openGUI(gui, player);
    }

    public void handleSuccess(Player player) {
        Runnable r = pendingSuccess.remove(player.getUniqueId());
        pendingFailure.remove(player.getUniqueId());
        if (r != null) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    public void handleFailure(Player player) {
        Runnable r = pendingFailure.remove(player.getUniqueId());
        pendingSuccess.remove(player.getUniqueId());
        if (r != null) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }
}