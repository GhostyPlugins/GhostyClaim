package de.gergh0stface.ghostyclaim.model;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an in-progress claim creation session for a player.
 */
public class ClaimSession {

    public static final int REQUIRED_TORCHES = 4;

    private final UUID playerUUID;
    private final List<Location> torchLocations = new ArrayList<>();
    private ItemStack[] savedInventory;
    private GameMode savedGameMode;
    private boolean wasFlying;
    private final long startTime;
    private Location savedLocation;

    // ─── Expansion mode ───────────────────────────────────────
    /** Whether this session is expanding an existing claim instead of creating a new one. */
    private boolean expansion = false;
    /** The UUID of the claim being expanded (null for new claims). */
    private UUID expandingClaimId = null;

    // ─── Border guide (exact bounds, adapts to claim size) ────
    /**
     * Exact world-space bounds of the guide border shown during this session.
     * For a new claim: player-pos ± half of config size.
     * For an expansion: existing claim bounds + configured buffer on each side.
     * Set to Integer.MIN/MAX_VALUE when not yet initialised.
     */
    private int borderMinX = Integer.MIN_VALUE;
    private int borderMinZ = Integer.MIN_VALUE;
    private int borderMaxX = Integer.MIN_VALUE;
    private int borderMaxZ = Integer.MIN_VALUE;
    private String borderWorld = null;

    public void setBorderBounds(int minX, int minZ, int maxX, int maxZ, String world) {
        this.borderMinX = minX; this.borderMinZ = minZ;
        this.borderMaxX = maxX; this.borderMaxZ = maxZ;
        this.borderWorld = world;
    }

    public boolean hasBorderBounds() { return borderWorld != null; }
    public int getBorderMinX() { return borderMinX; }
    public int getBorderMinZ() { return borderMinZ; }
    public int getBorderMaxX() { return borderMaxX; }
    public int getBorderMaxZ() { return borderMaxZ; }
    public String getBorderWorld() { return borderWorld; }

    /** @deprecated Use {@link #setBorderBounds} instead. */
    @Deprecated
    public void setBorderCenter(Location loc) {
        // kept for backwards compat — does nothing; bounds are set explicitly
    }
    /** @deprecated Use bounds accessors instead. */
    @Deprecated
    public Location getBorderCenter() { return null; }

    public ClaimSession(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.startTime = System.currentTimeMillis();
    }

    /** Mark this session as an expansion of an existing claim. */
    public void setExpansion(UUID claimId) {
        this.expansion = true;
        this.expandingClaimId = claimId;
    }

    public boolean isExpansion() { return expansion; }
    public UUID getExpandingClaimId() { return expandingClaimId; }

    public UUID getPlayerUUID() { return playerUUID; }

    public List<Location> getTorchLocations() { return torchLocations; }

    public void addTorch(Location location) {
        torchLocations.add(location.clone());
    }

    public boolean removeTorch(Location location) {
        return torchLocations.removeIf(l ->
                l.getBlockX() == location.getBlockX() &&
                l.getBlockY() == location.getBlockY() &&
                l.getBlockZ() == location.getBlockZ() &&
                l.getWorld() != null && location.getWorld() != null &&
                l.getWorld().equals(location.getWorld())
        );
    }

    public int getTorchCount() { return torchLocations.size(); }

    public boolean hasAllTorches() { return torchLocations.size() >= REQUIRED_TORCHES; }

    public ItemStack[] getSavedInventory() { return savedInventory; }
    public void setSavedInventory(ItemStack[] savedInventory) { this.savedInventory = savedInventory; }

    public GameMode getSavedGameMode() { return savedGameMode; }
    public void setSavedGameMode(GameMode savedGameMode) { this.savedGameMode = savedGameMode; }

    public boolean wasFlying() { return wasFlying; }
    public void setWasFlying(boolean wasFlying) { this.wasFlying = wasFlying; }

    public long getStartTime() { return startTime; }

    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    public Location getSavedLocation() { return savedLocation; }
    public void setSavedLocation(Location savedLocation) { this.savedLocation = savedLocation; }

    /**
     * Calculate the bounding box from placed torches (if 4 are placed).
     * Returns int[]{minX, minZ, maxX, maxZ} or null if not enough torches.
     */
    public int[] calculateBounds() {
        if (torchLocations.size() < REQUIRED_TORCHES) return null;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Location loc : torchLocations) {
            minX = Math.min(minX, loc.getBlockX());
            minZ = Math.min(minZ, loc.getBlockZ());
            maxX = Math.max(maxX, loc.getBlockX());
            maxZ = Math.max(maxZ, loc.getBlockZ());
        }

        return new int[]{minX, minZ, maxX, maxZ};
    }

    /**
     * Returns the world name of the first torch, or null if no torches placed.
     */
    public String getWorldName() {
        if (torchLocations.isEmpty()) return null;
        Location first = torchLocations.get(0);
        return first.getWorld() != null ? first.getWorld().getName() : null;
    }
}
