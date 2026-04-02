package de.gergh0stface.ghostyclaim.manager;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Integrates with LuckPerms to determine per-rank maximum claim counts.
 *
 * All permission nodes and the meta key are read from config.yml — no values
 * are hardcoded here. Admins can freely add / remove nodes in config.
 *
 * Priority order (highest wins):
 *   1. LuckPerms meta value  →  rank-permissions.meta-key  (any integer)
 *   2. Permission node       →  rank-permissions.permission-prefix + <n>
 *                               (only values in rank-permissions.permission-nodes are scanned)
 *   3. Config default        →  claims.max-per-player
 *
 * ── Quick setup examples ──────────────────────────────────────────────────
 *
 *   Method 1 (meta — unlimited range, no need to list value in config):
 *     /lp group default meta set ghostyclaim.max-claims 3
 *     /lp group vip     meta set ghostyclaim.max-claims 10
 *     /lp group admin   meta set ghostyclaim.max-claims 9999
 *
 *   Method 2 (permission node — value must exist in permission-nodes list):
 *     /lp group vip permission set ghostyclaim.maxclaims.10 true
 *
 *   To add a completely custom value for Method 2, add it to config:
 *     rank-permissions:
 *       permission-nodes:
 *         - 42    <- new custom value
 *     Then:  /lp group myrank permission set ghostyclaim.maxclaims.42 true
 * ─────────────────────────────────────────────────────────────────────────
 */
public class LuckPermsManager {

    private final GhostyClaim plugin;
    private LuckPerms luckPerms;
    private boolean available = false;

    public LuckPermsManager(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    // ─── Setup ────────────────────────────────────────────────

    public void setup() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("LuckPerms not found — using config default for max claims.");
            return;
        }
        try {
            luckPerms = LuckPermsProvider.get();
            available = true;
            plugin.getLogger().info("LuckPerms hooked — per-rank claim limits enabled.");
            logLoadedNodes();
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("LuckPerms API not ready: " + e.getMessage());
        }
    }

    public boolean isAvailable() { return available; }

    // ─── Core logic ───────────────────────────────────────────

    /**
     * Returns the maximum number of claims this player may own.
     * Checks (in order): LuckPerms meta → permission nodes → config default.
     */
    public int getMaxClaims(Player player) {
        int configDefault = plugin.getConfig().getInt("claims.max-per-player", 5);

        // No LuckPerms → always return config default
        if (!available || luckPerms == null) return configDefault;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return configDefault;

        // ── 1. LuckPerms meta key ─────────────────────────────
        String metaKey = plugin.getConfig()
                .getString("rank-permissions.meta-key", "ghostyclaim.max-claims");
        try {
            // user.getQueryOptions() returns QueryOptions directly in LP5
            QueryOptions opts = user.getQueryOptions();

            String metaValue = user.getCachedData()
                    .getMetaData(opts)
                    .getMetaValue(metaKey);

            if (metaValue != null) {
                int parsed = Integer.parseInt(metaValue.trim());
                if (parsed >= 0) return parsed;
            }
        } catch (NumberFormatException | IllegalStateException ignored) {}

        // ── 2. Permission nodes from config ───────────────────
        //    Loaded, sorted descending, checked in that order.
        List<Integer> nodes = getPermissionNodes();
        String prefix = plugin.getConfig()
                .getString("rank-permissions.permission-prefix", "ghostyclaim.maxclaims.");

        for (int n : nodes) {
            if (player.hasPermission(prefix + n)) {
                return n;
            }
        }

        // ── 3. Config default ─────────────────────────────────
        return configDefault;
    }

    // ─── Config helpers ───────────────────────────────────────

    /**
     * Load the permission node values from config, sorted highest → lowest.
     * Silently skips any non-integer entries.
     */
    public List<Integer> getPermissionNodes() {
        List<Integer> result = new ArrayList<>();
        List<?> raw = plugin.getConfig().getList("rank-permissions.permission-nodes");
        if (raw == null) return result;

        for (Object entry : raw) {
            try {
                result.add(Integer.parseInt(entry.toString().trim()));
            } catch (NumberFormatException ignored) {}
        }

        // Sort descending so the highest matching node always wins
        result.sort(Comparator.reverseOrder());
        return result;
    }

    /**
     * Returns the full permission node string for a given claim count.
     * Useful for displaying to admins.
     */
    public String getPermissionNode(int count) {
        String prefix = plugin.getConfig()
                .getString("rank-permissions.permission-prefix", "ghostyclaim.maxclaims.");
        return prefix + count;
    }

    /**
     * Returns the configured meta key name.
     */
    public String getMetaKey() {
        return plugin.getConfig()
                .getString("rank-permissions.meta-key", "ghostyclaim.max-claims");
    }

    // ─── Logging ──────────────────────────────────────────────

    private void logLoadedNodes() {
        List<Integer> nodes = getPermissionNodes();
        String prefix = plugin.getConfig()
                .getString("rank-permissions.permission-prefix", "ghostyclaim.maxclaims.");
        plugin.getLogger().info("LuckPerms: meta-key=\"" + getMetaKey() + "\""
                + " | permission-prefix=\"" + prefix + "\""
                + " | " + nodes.size() + " node value(s) loaded: " + nodes);
    }
}
