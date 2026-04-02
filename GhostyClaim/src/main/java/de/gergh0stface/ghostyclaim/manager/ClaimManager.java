package de.gergh0stface.ghostyclaim.manager;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.database.Database;
import de.gergh0stface.ghostyclaim.model.Claim;
import org.bukkit.Location;

import java.util.*;

/**
 * Central manager for all claims.
 * Keeps an in-memory cache and delegates persistence to the Database.
 */
public class ClaimManager {

    private final GhostyClaim plugin;
    private final Database database;

    // In-memory cache: claimId -> Claim
    private final Map<UUID, Claim> claimCache = new HashMap<>();

    public ClaimManager(GhostyClaim plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Load all claims from the database into memory.
     */
    public void loadAll() {
        claimCache.clear();
        List<Claim> all = database.getAllClaims();
        for (Claim c : all) {
            claimCache.put(c.getId(), c);
        }
        plugin.getLogger().info("Loaded " + claimCache.size() + " claims.");
    }

    /**
     * Save and register a new claim.
     */
    public void addClaim(Claim claim) {
        claimCache.put(claim.getId(), claim);
        database.saveClaim(claim);
    }

    /**
     * Update (save) an existing claim.
     */
    public void updateClaim(Claim claim) {
        database.saveClaim(claim);
    }

    /**
     * Delete a claim.
     */
    public void deleteClaim(UUID claimId) {
        claimCache.remove(claimId);
        database.deleteClaim(claimId);
    }

    /**
     * Get a claim by ID from cache.
     */
    public Claim getClaim(UUID claimId) {
        return claimCache.get(claimId);
    }

    /**
     * Find the claim that contains the given location, or null.
     */
    public Claim getClaimAt(Location location) {
        for (Claim c : claimCache.values()) {
            if (c.contains(location)) return c;
        }
        return null;
    }

    /**
     * Get all claims owned by a player.
     */
    public List<Claim> getClaimsForPlayer(UUID playerUUID) {
        List<Claim> result = new ArrayList<>();
        for (Claim c : claimCache.values()) {
            if (c.getOwnerUUID().equals(playerUUID)) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * Count how many claims a player owns.
     */
    public int getClaimCount(UUID playerUUID) {
        int count = 0;
        for (Claim c : claimCache.values()) {
            if (c.getOwnerUUID().equals(playerUUID)) count++;
        }
        return count;
    }

    /**
     * Check if a rectangular area overlaps any existing claim.
     * @return the overlapping claim, or null if clear
     */
    public Claim checkOverlap(String worldName, int minX, int minZ, int maxX, int maxZ) {
        return checkOverlap(worldName, minX, minZ, maxX, maxZ, null);
    }

    /**
     * Check if a rectangular area overlaps any existing claim, excluding one claim by ID.
     * Used during expansion to ignore the claim being resized.
     * @return the overlapping claim, or null if clear
     */
    public Claim checkOverlap(String worldName, int minX, int minZ, int maxX, int maxZ,
                               java.util.UUID excludeClaimId) {
        for (Claim c : claimCache.values()) {
            if (excludeClaimId != null && c.getId().equals(excludeClaimId)) continue;
            if (c.overlaps(worldName, minX, minZ, maxX, maxZ)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Update the owner name in all claims for a given player UUID.
     * Called when a player changes their Minecraft username.
     */
    public int updateOwnerName(UUID playerUUID, String newName) {
        int updated = 0;
        for (Claim c : claimCache.values()) {
            boolean changed = false;
            if (c.getOwnerUUID().equals(playerUUID) && !c.getOwnerName().equals(newName)) {
                c.setOwnerName(newName);
                changed = true;
            }
            // Update trusted player names too
            if (c.getTrustedPlayers().containsKey(playerUUID)) {
                String currentName = c.getTrustedPlayers().get(playerUUID);
                if (!currentName.equals(newName)) {
                    c.addTrusted(playerUUID, newName);
                    changed = true;
                }
            }
            if (changed) {
                database.saveClaim(c);
                updated++;
            }
        }
        return updated;
    }

    /**
     * Get all claims in the cache.
     */
    public Collection<Claim> getAllClaims() {
        return Collections.unmodifiableCollection(claimCache.values());
    }

    /**
     * Get the max claims allowed for a specific player (respects LuckPerms).
     */
    public int getMaxClaimsForPlayer(org.bukkit.entity.Player player) {
        return plugin.getLuckPermsManager().getMaxClaims(player);
    }

    /**
     * Get the global config default for max claims per player.
     * @deprecated Use {@link #getMaxClaimsForPlayer(org.bukkit.entity.Player)} instead.
     */
    @Deprecated
    public int getMaxClaimsPerPlayer() {
        return plugin.getConfig().getInt("claims.max-per-player", 5);
    }

    /**
     * Get the min claim area from config.
     */
    public int getMinArea() {
        return plugin.getConfig().getInt("claims.min-area", 25);
    }

    /**
     * Get the max claim area from config.
     */
    public int getMaxArea() {
        return plugin.getConfig().getInt("claims.max-area", 50000);
    }

    /**
     * Get the min claim dimension from config.
     */
    public int getMinDimension() {
        return plugin.getConfig().getInt("claims.min-dimension", 5);
    }

    /**
     * Check if a world is allowed for claiming.
     */
    public boolean isWorldAllowed(String worldName) {
        List<String> blocked = plugin.getConfig().getStringList("claims.blocked-worlds");
        if (blocked.contains(worldName)) return false;
        List<String> allowed = plugin.getConfig().getStringList("claims.allowed-worlds");
        if (!allowed.isEmpty() && !allowed.contains(worldName)) return false;
        return true;
    }
}
