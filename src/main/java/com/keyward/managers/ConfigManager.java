package com.keyward.managers;

import com.keyward.Keyward;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {
    private final Keyward plugin;
    private FileConfiguration messages;

    public ConfigManager(Keyward plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        loadMessages();
    }

    public void reload() {
        plugin.reloadConfig();
        loadMessages();
    }

    private void loadMessages() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStreamReader defReader = new InputStreamReader(
                plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(defReader));
        } catch (Exception ignored) {
        }
    }

    public String prefix() {
        return messages.getString("prefix", "&8[&bKeyward&8] ");
    }

    public String msg(String path, Object... args) {
        String raw = messages.getString(path, path);
        for (int i = 0; i + 1 < args.length; i += 2) {
            raw = raw.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return ChatColor.translateAlternateColorCodes('&', prefix() + raw);
    }

    public int argon2MemoryKb() {
        return plugin.getConfig().getInt("security.argon2.memory-kb", 65536);
    }

    public int argon2Iterations() {
        return plugin.getConfig().getInt("security.argon2.iterations", 3);
    }

    public int argon2Parallelism() {
        return plugin.getConfig().getInt("security.argon2.parallelism", 1);
    }

    public int minPasswordLength() {
        return plugin.getConfig().getInt("security.password.min-length", 8);
    }

    public int sessionTimeoutMinutes() {
        return plugin.getConfig().getInt("security.session.timeout-minutes", 10);
    }

    public int maxLoginAttempts() {
        return plugin.getConfig().getInt("security.lockout.max-attempts", 5);
    }

    public int baseCooldownSeconds() {
        return plugin.getConfig().getInt("security.lockout.base-cooldown-seconds", 30);
    }

    public int attackFailureThreshold() {
        return plugin.getConfig().getInt("security.attack-mode.failure-threshold", 20);
    }

    public int attackUniqueIpThreshold() {
        return plugin.getConfig().getInt("security.attack-mode.unique-ip-threshold", 5);
    }

    public int attackWindowSeconds() {
        return plugin.getConfig().getInt("security.attack-mode.window-seconds", 60);
    }

    public int limboTimeoutSeconds() {
        return plugin.getConfig().getInt("security.limbo.timeout-seconds", 60);
    }

    public boolean captchaEnabled() {
        return plugin.getConfig().getBoolean("security.captcha.enabled", true);
    }

    public boolean premiumAutoVerify() {
        return plugin.getConfig().getBoolean("premium.auto-verify", true);
    }
}