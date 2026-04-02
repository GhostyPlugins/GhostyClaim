package de.gergh0stface.ghostyclaim.listener;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

/**
 * Handles two kinds of chat input triggered from GUIs:
 *   1. Rename a claim  (renameWaiting map)
 *   2. Change owner    (changeOwnerWaiting map, admin-only)
 */
public class ChatRenameListener implements Listener {

    private final GhostyClaim plugin;

    public ChatRenameListener(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // ── Change-owner input (admin) ─────────────────────────
        UUID changeOwnerClaimId = plugin.getChangeOwnerWaiting().get(player.getUniqueId());
        if (changeOwnerClaimId != null) {
            event.setCancelled(true);
            plugin.getChangeOwnerWaiting().remove(player.getUniqueId());
            handleChangeOwner(player, changeOwnerClaimId, event.getMessage().trim());
            return;
        }

        // ── Rename input ───────────────────────────────────────
        UUID renameClaimId = plugin.getRenameWaiting().get(player.getUniqueId());
        if (renameClaimId == null) return;

        event.setCancelled(true);
        plugin.getRenameWaiting().remove(player.getUniqueId());
        handleRename(player, renameClaimId, event.getMessage().trim());
    }

    // ─── Rename ───────────────────────────────────────────────

    private void handleRename(Player player, UUID claimId, String input) {
        if (isCancelled(input)) {
            plugin.getLangManager().send(player, "session.cancelled");
            return;
        }
        int maxLen = 32;
        if (input.length() > maxLen) {
            plugin.getLangManager().send(player, "claim.name-too-long", "max", maxLen);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Claim claim = plugin.getClaimManager().getClaim(claimId);
            if (claim == null) { plugin.getLangManager().send(player, "claim.not-found"); return; }
            if (!claim.getOwnerUUID().equals(player.getUniqueId())
                    && !player.hasPermission("ghostyclaim.admin")) {
                plugin.getLangManager().send(player, "claim.not-owner"); return;
            }
            claim.setName(input);
            plugin.getClaimManager().updateClaim(claim);
            plugin.getLangManager().send(player, "claim.renamed", "name", input);
            // Refresh bossbar if still inside the claim
            Claim current = plugin.getClaimManager().getClaimAt(player.getLocation());
            if (current != null && current.getId().equals(claimId)) {
                plugin.getPlayerMoveListener().refreshBossBar(player);
            }
        });
    }

    // ─── Change Owner ─────────────────────────────────────────

    private void handleChangeOwner(Player admin, UUID claimId, String input) {
        if (isCancelled(input)) {
            plugin.getLangManager().send(admin, "session.cancelled");
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Claim claim = plugin.getClaimManager().getClaim(claimId);
            if (claim == null) { plugin.getLangManager().send(admin, "claim.not-found"); return; }
            if (!admin.hasPermission("ghostyclaim.admin")) {
                plugin.getLangManager().send(admin, "general.no-permission"); return;
            }

            // Find target player (online or offline)
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                plugin.getLangManager().send(admin, "general.player-not-found", "player", input);
                return;
            }

            String oldOwner = claim.getOwnerName();
            claim.setOwnerUUID(target.getUniqueId());
            claim.setOwnerName(target.getName() != null ? target.getName() : input);
            plugin.getClaimManager().updateClaim(claim);

            plugin.getLangManager().send(admin, "admin-gui.owner-changed",
                    "name",     claim.getName(),
                    "old",      oldOwner,
                    "new",      claim.getOwnerName());

            plugin.getLogger().info("[Admin] " + admin.getName()
                    + " transferred claim \"" + claim.getName()
                    + "\" from " + oldOwner + " to " + claim.getOwnerName());
        });
    }

    private boolean isCancelled(String input) {
        return input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("abbrechen");
    }
}
