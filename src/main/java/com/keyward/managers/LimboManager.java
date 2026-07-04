package com.keyward.managers;

import com.keyward.Keyward;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimboManager {
    private final Keyward plugin;
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> frozenAt = new ConcurrentHashMap<>();

    public LimboManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public void enterLimbo(Player player) {
        int timeoutSeconds = plugin.getConfigManager().limboTimeoutSeconds();
        frozenAt.put(player.getUniqueId(), player.getLocation());
        BossBar bossBar = Bukkit.createBossBar("\u00A7eAuthenticate to continue!", BarColor.RED, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);
        bossBars.put(player.getUniqueId(), bossBar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = timeoutSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTask();
                    return;
                }
                secondsLeft--;
                bossBar.setProgress(Math.max(0, (double) secondsLeft / timeoutSeconds));
                bossBar.setTitle("\u00A7eAuthenticate! \u00A7c" + secondsLeft + "s remaining");
                if (secondsLeft <= 0) {
                    player.kickPlayer(plugin.getConfigManager().msg("limbo.timeout"));
                    cancelTask();
                }
            }

            private void cancelTask() {
                BukkitTask t = tasks.remove(player.getUniqueId());
                if (t != null) {
                    t.cancel();
                }
            }
        }, 20L, 20L);
        tasks.put(player.getUniqueId(), task);
    }

    public void exitLimbo(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
        BukkitTask task = tasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        frozenAt.remove(uuid);
    }

    public boolean isFrozenLocation(Player player) {
        return frozenAt.containsKey(player.getUniqueId());
    }

    public Location getFrozenLocation(Player player) {
        return frozenAt.get(player.getUniqueId());
    }
}