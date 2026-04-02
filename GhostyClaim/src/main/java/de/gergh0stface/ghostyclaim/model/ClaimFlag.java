package de.gergh0stface.ghostyclaim.model;

import org.bukkit.Material;

/**
 * All available flags for a claim.
 * Each flag has a config key, a default value, and a display material for the GUI.
 */
public enum ClaimFlag {

    TNT("tnt", false, Material.TNT),
    FIRE_SPREAD("fire_spread", false, Material.FLINT_AND_STEEL),
    JOIN_MESSAGES("join_messages", true, Material.OAK_SIGN),
    LEAVE_MESSAGES("leave_messages", true, Material.SPRUCE_SIGN),
    PVP("pvp", false, Material.IRON_SWORD),
    MOB_SPAWNING("mob_spawning", true, Material.ZOMBIE_SPAWN_EGG),
    EXPLOSIONS("explosions", false, Material.FIRE_CHARGE),
    CREEPER_DAMAGE("creeper_damage", false, Material.CREEPER_HEAD),
    LEAF_DECAY("leaf_decay", true, Material.OAK_LEAVES),
    ICE_MELT("ice_melt", true, Material.ICE),
    SNOW_MELT("snow_melt", true, Material.SNOW_BLOCK),
    ANIMAL_DAMAGE("animal_damage", false, Material.COOKED_BEEF),
    INTERACT("interact", false, Material.CHEST);

    private final String key;
    private final boolean defaultValue;
    private final Material guiMaterial;

    ClaimFlag(String key, boolean defaultValue, Material guiMaterial) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.guiMaterial = guiMaterial;
    }

    public String getKey() {
        return key;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    public Material getGuiMaterial() {
        return guiMaterial;
    }

    /**
     * Find a ClaimFlag by its config key.
     */
    public static ClaimFlag fromKey(String key) {
        for (ClaimFlag flag : values()) {
            if (flag.key.equalsIgnoreCase(key)) return flag;
        }
        return null;
    }
}
