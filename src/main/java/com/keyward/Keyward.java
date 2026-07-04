package com.keyward;

import com.keyward.commands.ChangePasswordCommand;
import com.keyward.commands.KeywardCommand;
import com.keyward.commands.LoginCommand;
import com.keyward.commands.RegisterCommand;
import com.keyward.commands.TwoFACommand;
import com.keyward.inventory.gui.GUIListener;
import com.keyward.inventory.gui.GUIManager;
import com.keyward.listeners.PlayerConnectionListener;
import com.keyward.managers.AttackModeManager;
import com.keyward.managers.AuditLogManager;
import com.keyward.managers.AuthManager;
import com.keyward.managers.CaptchaManager;
import com.keyward.managers.ConfigManager;
import com.keyward.managers.DatabaseManager;
import com.keyward.managers.IpFlagManager;
import com.keyward.managers.LimboManager;
import com.keyward.managers.SessionManager;
import com.keyward.util.BreachedPasswordList;
import com.keyward.util.MojangAPI;
import com.keyward.util.TotpManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class Keyward extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private AuthManager authManager;
    private SessionManager sessionManager;
    private CaptchaManager captchaManager;
    private AttackModeManager attackModeManager;
    private AuditLogManager auditLogManager;
    private IpFlagManager ipFlagManager;
    private LimboManager limboManager;
    private TotpManager totpManager;
    private MojangAPI mojangAPI;
    private BreachedPasswordList breachedPasswordList;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();

        this.auditLogManager = new AuditLogManager(this);
        this.authManager = new AuthManager(this);
        this.sessionManager = new SessionManager(this);
        this.attackModeManager = new AttackModeManager(this);
        this.ipFlagManager = new IpFlagManager(this);
        this.limboManager = new LimboManager(this);
        this.totpManager = new TotpManager();
        this.mojangAPI = new MojangAPI();
        this.breachedPasswordList = new BreachedPasswordList(this);

        this.guiManager = new GUIManager();
        this.captchaManager = new CaptchaManager(this);

        Bukkit.getPluginManager().registerEvents(new GUIListener(guiManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this));
        getCommand("2fa").setExecutor(new TwoFACommand(this));
        getCommand("keyward").setExecutor(new KeywardCommand(this));

        getLogger().info("Keyward has been enabled. Nobody gets in unverified.");
    }

    @Override
    public void onDisable() {
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        getLogger().info("Keyward has been disabled.");
    }
}