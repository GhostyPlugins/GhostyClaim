package de.gergh0stface.ghostyclaim;

import de.gergh0stface.ghostyclaim.command.*;
import de.gergh0stface.ghostyclaim.database.*;
import de.gergh0stface.ghostyclaim.listener.*;
import de.gergh0stface.ghostyclaim.manager.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
/**
 * GhostyClaim — Main plugin class.
 * Author: Ger_Gh0stface
 * Supports: Paper & Spigot 1.21 – 1.21.x
 */
public class GhostyClaim extends JavaPlugin {

    // ─── Managers ─────────────────────────────────────────────
    private LangManager       langManager;
    private EconomyManager    economyManager;
    private LuckPermsManager  luckPermsManager;
    private ClaimManager      claimManager;
    private SessionManager    sessionManager;
    private VisualizerManager visualizerManager;
    private Database          database;

    // ─── Listeners (kept as fields for cross-access) ──────────
    private PlayerMoveListener playerMoveListener;

    // ─── Pending rename map (player UUID → claim UUID) ────────
    private final Map<UUID, UUID> renameWaiting      = new HashMap<>();
    // ─── Pending change-owner map (admin UUID → claim UUID) ───
    private final Map<UUID, UUID> changeOwnerWaiting = new HashMap<>();

    // ─── Lifecycle ────────────────────────────────────────────

    @Override
    public void onEnable() {
        // 1. Save default configs
        saveDefaultConfig();

        // 2. Initialize language
        langManager = new LangManager(this);
        langManager.load();

        // 3. Economy (optional)
        economyManager = new EconomyManager(this);
        economyManager.setup();

        // 3b. LuckPerms (optional)
        luckPermsManager = new LuckPermsManager(this);
        luckPermsManager.setup();

        // 4. Database
        database = buildDatabase();
        database.init();

        // 5. Managers
        claimManager      = new ClaimManager(this, database);
        claimManager.loadAll();

        sessionManager    = new SessionManager(this);
        sessionManager.startTimeoutTask();

        visualizerManager = new VisualizerManager(this);
        visualizerManager.startTask();
        visualizerManager.startGuideBorderTask();

        // 6. Listeners
        playerMoveListener = new PlayerMoveListener(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ClaimCreationListener(this), this);
        pm.registerEvents(new ClaimProtectionListener(this), this);
        pm.registerEvents(playerMoveListener, this);
        pm.registerEvents(new PlayerNameChangeListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new ChatRenameListener(this), this);

        // 7. Commands
        var claimCmd = getCommand("claim");
        if (claimCmd != null) {
            ClaimCommand cc = new ClaimCommand(this);
            claimCmd.setExecutor(cc);
            claimCmd.setTabCompleter(cc);
        }

        var reloadCmd = getCommand("claimreload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(new ClaimReloadCommand(this));
        }

        getLogger().info("GhostyClaim v" + getDescription().getVersion()
                + " enabled! Author: Ger_Gh0stface");
    }

    @Override
    public void onDisable() {
        // Cancel all active sessions
        if (sessionManager != null) {
            sessionManager.stopTimeoutTask();
            sessionManager.cancelAll();
        }

        // Stop visualizer
        if (visualizerManager != null) {
            visualizerManager.stopTask();
        }

        // Remove all boss bars
        if (playerMoveListener != null) {
            playerMoveListener.removeAll();
        }

        // Save / close database
        if (database != null) {
            database.close();
        }

        getLogger().info("GhostyClaim disabled.");
    }

    // ─── Reload ───────────────────────────────────────────────

    /**
     * Reload config, language, and claim cache.
     */
    public void reload() {
        reloadConfig();
        langManager.load();

        // Restart visualizer with possibly new interval
        visualizerManager.stopTask();
        visualizerManager.startTask();
        visualizerManager.startGuideBorderTask();

        // Reload claims
        claimManager.loadAll();

        // Restart session timeout
        sessionManager.stopTimeoutTask();
        sessionManager.startTimeoutTask();

        getLogger().info("GhostyClaim reloaded.");
    }

    // ─── Getters ──────────────────────────────────────────────

    public LangManager       getLangManager()       { return langManager;       }
    public EconomyManager    getEconomyManager()    { return economyManager;    }
    public LuckPermsManager  getLuckPermsManager()  { return luckPermsManager;  }
    public ClaimManager      getClaimManager()      { return claimManager;      }
    public SessionManager    getSessionManager()    { return sessionManager;    }
    public VisualizerManager getVisualizerManager() { return visualizerManager; }
    public Database          getDatabase()          { return database;          }
    public PlayerMoveListener getPlayerMoveListener(){ return playerMoveListener; }
    public Map<UUID, UUID>   getRenameWaiting()      { return renameWaiting;       }
    public Map<UUID, UUID>   getChangeOwnerWaiting() { return changeOwnerWaiting;  }

    // ─── Helpers ──────────────────────────────────────────────

    private Database buildDatabase() {
        String type = getConfig().getString("database.type", "yml");
        if ("mysql".equalsIgnoreCase(type)) {
            getLogger().info("Using MySQL database.");
            return new MySQLDatabase(this);
        }
        getLogger().info("Using YML database.");
        return new YMLDatabase(this);
    }
}
