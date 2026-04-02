package de.gergh0stface.ghostyclaim.listener;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

/**
 * Detects when a player logs in with a different name than what is stored
 * in the claims database, and updates all their claims accordingly.
 *
 * This covers the Minecraft username-change scenario: the UUID stays the
 * same but the display name may differ from what was saved.
 */
public class PlayerNameChangeListener implements Listener {

    private final GhostyClaim plugin;

    public PlayerNameChangeListener(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;

        UUID uuid        = event.getPlayer().getUniqueId();
        String loginName = event.getPlayer().getName();

        // Run async so we don't block the login thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int updated = plugin.getClaimManager().updateOwnerName(uuid, loginName);
            if (updated > 0) {
                plugin.getLogger().info("Updated owner name for " + loginName
                        + " in " + updated + " claim(s).");

                // Notify the player once they are fully online
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    var player = plugin.getServer().getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        plugin.getLangManager().send(player, "player-update.name-changed",
                                "old", "?", "new", loginName);
                    }
                }, 40L);
            }
        });
    }
}
