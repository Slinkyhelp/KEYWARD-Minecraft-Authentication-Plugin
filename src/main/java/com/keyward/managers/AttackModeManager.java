package com.keyward.managers;

import com.keyward.Keyward;
import org.bukkit.Bukkit;

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArraySet;

public class AttackModeManager {
    private final Keyward plugin;
    private final ConcurrentLinkedDeque<Failure> failures = new ConcurrentLinkedDeque<>();
    private volatile boolean active = false;

    public AttackModeManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void recordFailure(String ipHash) {
        long now = System.currentTimeMillis();
        failures.add(new Failure(ipHash, now));
        long windowMs = plugin.getConfigManager().attackWindowSeconds() * 1000L;
        while (!failures.isEmpty() && now - failures.peekFirst().timestamp > windowMs) {
            failures.pollFirst();
        }
        evaluate();
    }

    private void evaluate() {
        int failureThreshold = plugin.getConfigManager().attackFailureThreshold();
        int uniqueIpThreshold = plugin.getConfigManager().attackUniqueIpThreshold();
        Set<String> uniqueIps = new CopyOnWriteArraySet<>();
        for (Failure f : failures) {
            uniqueIps.add(f.ipHash);
        }
        boolean shouldBeActive = failures.size() >= failureThreshold && uniqueIps.size() >= uniqueIpThreshold;
        if (shouldBeActive && !active) {
            active = true;
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.broadcast(plugin.getConfigManager().msg("keyward.attack-mode-on"), "keyward.admin"));
            plugin.getAuditLogManager().log(null, "attack_mode_activated", "system", null, "{}");
        } else if (!shouldBeActive && active) {
            active = false;
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.broadcast(plugin.getConfigManager().msg("keyward.attack-mode-off"), "keyward.admin"));
            plugin.getAuditLogManager().log(null, "attack_mode_deactivated", "system", null, "{}");
        }
    }

    private static class Failure {
        final String ipHash;
        final long timestamp;

        Failure(String ipHash, long timestamp) {
            this.ipHash = ipHash;
            this.timestamp = timestamp;
        }
    }
}