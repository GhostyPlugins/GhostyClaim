package de.gergh0stface.ghostyclaim.database;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimFlag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * YML file-based database implementation.
 */
public class YMLDatabase implements Database {

    private final GhostyClaim plugin;
    private File dataFile;
    private FileConfiguration data;

    public YMLDatabase(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create data.yml!", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        plugin.getLogger().info("YML database initialized.");
    }

    @Override
    public synchronized void saveClaim(Claim claim) {
        String path = "claims." + claim.getId().toString();

        data.set(path + ".owner-uuid", claim.getOwnerUUID().toString());
        data.set(path + ".owner-name", claim.getOwnerName());
        data.set(path + ".world", claim.getWorldName());
        data.set(path + ".min-x", claim.getMinX());
        data.set(path + ".min-z", claim.getMinZ());
        data.set(path + ".max-x", claim.getMaxX());
        data.set(path + ".max-z", claim.getMaxZ());
        data.set(path + ".name", claim.getName());

        // Flags
        for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
            data.set(path + ".flags." + entry.getKey().getKey(), entry.getValue());
        }

        // Trusted players
        ConfigurationSection trustedSection = data.createSection(path + ".trusted");
        for (Map.Entry<UUID, String> entry : claim.getTrustedPlayers().entrySet()) {
            trustedSection.set(entry.getKey().toString(), entry.getValue());
        }

        saveFile();
    }

    @Override
    public synchronized void deleteClaim(UUID claimId) {
        data.set("claims." + claimId.toString(), null);
        saveFile();
    }

    @Override
    public Claim getClaim(UUID claimId) {
        String path = "claims." + claimId.toString();
        if (!data.contains(path)) return null;
        return loadClaim(claimId.toString(), data.getConfigurationSection(path));
    }

    @Override
    public List<Claim> getClaimsForPlayer(UUID playerUUID) {
        List<Claim> result = new ArrayList<>();
        ConfigurationSection claimsSection = data.getConfigurationSection("claims");
        if (claimsSection == null) return result;

        for (String key : claimsSection.getKeys(false)) {
            ConfigurationSection section = claimsSection.getConfigurationSection(key);
            if (section == null) continue;
            String ownerStr = section.getString("owner-uuid");
            if (ownerStr != null && UUID.fromString(ownerStr).equals(playerUUID)) {
                Claim c = loadClaim(key, section);
                if (c != null) result.add(c);
            }
        }
        return result;
    }

    @Override
    public List<Claim> getAllClaims() {
        List<Claim> result = new ArrayList<>();
        ConfigurationSection claimsSection = data.getConfigurationSection("claims");
        if (claimsSection == null) return result;

        for (String key : claimsSection.getKeys(false)) {
            ConfigurationSection section = claimsSection.getConfigurationSection(key);
            Claim c = loadClaim(key, section);
            if (c != null) result.add(c);
        }
        return result;
    }

    @Override
    public void close() {
        saveFile();
    }

    // ─── Helpers ──────────────────────────────────────────────

    private Claim loadClaim(String idStr, ConfigurationSection section) {
        if (section == null) return null;
        try {
            UUID id = UUID.fromString(idStr);
            UUID ownerUUID = UUID.fromString(Objects.requireNonNull(section.getString("owner-uuid")));
            String ownerName = section.getString("owner-name", "Unknown");
            String world = section.getString("world", "world");
            int minX = section.getInt("min-x");
            int minZ = section.getInt("min-z");
            int maxX = section.getInt("max-x");
            int maxZ = section.getInt("max-z");
            String name = section.getString("name", ownerName + "'s Claim");

            Claim claim = new Claim(id, ownerUUID, ownerName, world, minX, minZ, maxX, maxZ, name);

            // Load flags
            ConfigurationSection flagsSection = section.getConfigurationSection("flags");
            if (flagsSection != null) {
                for (String flagKey : flagsSection.getKeys(false)) {
                    ClaimFlag flag = ClaimFlag.fromKey(flagKey);
                    if (flag != null) {
                        claim.setFlag(flag, flagsSection.getBoolean(flagKey));
                    }
                }
            }

            // Load trusted players
            ConfigurationSection trustedSection = section.getConfigurationSection("trusted");
            if (trustedSection != null) {
                for (String uuidStr : trustedSection.getKeys(false)) {
                    try {
                        UUID trustedUUID = UUID.fromString(uuidStr);
                        String trustedName = trustedSection.getString(uuidStr, "Unknown");
                        claim.addTrusted(trustedUUID, trustedName);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            return claim;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load claim '" + idStr + "': " + e.getMessage());
            return null;
        }
    }

    private void saveFile() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml!", e);
        }
    }
}
