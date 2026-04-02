package de.gergh0stface.ghostyclaim.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * Represents a claimed area in a world.
 */
public class Claim {

    private final UUID id;
    private UUID ownerUUID;
    private String ownerName;
    private final String worldName;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private String name;
    private final Map<ClaimFlag, Boolean> flags;
    private final Map<UUID, String> trustedPlayers; // UUID -> player name

    public Claim(UUID id, UUID ownerUUID, String ownerName, String worldName,
                 int minX, int minZ, int maxX, int maxZ, String name) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.name = name;
        this.flags = new EnumMap<>(ClaimFlag.class);
        this.trustedPlayers = new HashMap<>();

        // Initialize defaults
        for (ClaimFlag flag : ClaimFlag.values()) {
            this.flags.put(flag, flag.getDefaultValue());
        }
    }

    // ─── Identity ─────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID ownerUUID) { this.ownerUUID = ownerUUID; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getWorldName() { return worldName; }

    // ─── Boundaries ───────────────────────────────────────────

    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }

    public void setMinX(int minX) { this.minX = minX; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }

    /**
     * Returns the total area (width * length) of this claim.
     */
    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    /**
     * Returns true if the given location is inside this claim (2D, ignores Y).
     */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Returns true if this claim's area overlaps with the given rectangle.
     */
    public boolean overlaps(String world, int oMinX, int oMinZ, int oMaxX, int oMaxZ) {
        if (!this.worldName.equals(world)) return false;
        return !(oMaxX < minX || oMinX > maxX || oMaxZ < minZ || oMinZ > maxZ);
    }

    /**
     * Get the center location of this claim (at world sea level).
     */
    public Location getCenter() {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, (minX + maxX) / 2.0, w.getSeaLevel(), (minZ + maxZ) / 2.0);
    }

    // ─── Name ─────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // ─── Flags ────────────────────────────────────────────────

    public boolean getFlag(ClaimFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultValue());
    }

    public void setFlag(ClaimFlag flag, boolean value) {
        flags.put(flag, value);
    }

    public Map<ClaimFlag, Boolean> getFlags() {
        return Collections.unmodifiableMap(flags);
    }

    public void setFlags(Map<ClaimFlag, Boolean> flags) {
        this.flags.clear();
        this.flags.putAll(flags);
    }

    // ─── Trust ────────────────────────────────────────────────

    public boolean isTrusted(UUID uuid) {
        return uuid.equals(ownerUUID) || trustedPlayers.containsKey(uuid);
    }

    public void addTrusted(UUID uuid, String name) {
        trustedPlayers.put(uuid, name);
    }

    public void removeTrusted(UUID uuid) {
        trustedPlayers.remove(uuid);
    }

    public Map<UUID, String> getTrustedPlayers() {
        return Collections.unmodifiableMap(trustedPlayers);
    }

    public void setTrustedPlayers(Map<UUID, String> trusted) {
        this.trustedPlayers.clear();
        this.trustedPlayers.putAll(trusted);
    }

    // ─── Utility ──────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Claim)) return false;
        return id.equals(((Claim) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Claim{id=" + id + ", owner=" + ownerName + ", world=" + worldName
                + ", area=(" + minX + "," + minZ + ")-(" + maxX + "," + maxZ + ")}";
    }
}
