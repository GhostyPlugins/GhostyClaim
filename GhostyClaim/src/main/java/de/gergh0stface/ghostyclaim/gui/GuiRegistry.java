package de.gergh0stface.ghostyclaim.gui;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registry that maps players to their currently open GUI session.
 */
public class GuiRegistry {

    private static final Map<UUID, GuiSession> SESSIONS = new HashMap<>();

    private GuiRegistry() {}

    public static void register(Player player, GuiSession session) {
        SESSIONS.put(player.getUniqueId(), session);
    }

    public static GuiSession get(Player player) {
        return SESSIONS.get(player.getUniqueId());
    }

    public static void remove(Player player) {
        SESSIONS.remove(player.getUniqueId());
    }

    public static boolean has(Player player) {
        return SESSIONS.containsKey(player.getUniqueId());
    }
}
