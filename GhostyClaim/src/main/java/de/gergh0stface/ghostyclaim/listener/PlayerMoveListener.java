package de.gergh0stface.ghostyclaim.listener;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimFlag;
import de.gergh0stface.ghostyclaim.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

import java.util.*;

/**
 * Handles player movement across claim borders:
 * - Title display on enter/leave
 * - BossBar with claim name above the screen
 * - Custom join/leave messages (flag based)
 */
public class PlayerMoveListener implements Listener {

    private final GhostyClaim plugin;
    // Player UUID -> current BossBar
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    // Player UUID -> UUID of claim they were last inside
    private final Map<UUID, UUID> lastClaim = new HashMap<>();

    public PlayerMoveListener(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process if the player moved to a different block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        Claim fromClaim = plugin.getClaimManager().getClaimAt(event.getFrom());
        Claim toClaim   = plugin.getClaimManager().getClaimAt(event.getTo());

        UUID fromId = fromClaim == null ? null : fromClaim.getId();
        UUID toId   = toClaim   == null ? null : toClaim.getId();

        if (Objects.equals(fromId, toId)) return; // no border crossed

        // ── Left a claim ──────────────────────────────────────
        if (fromClaim != null && !fromClaim.equals(toClaim)) {
            removeBossBar(player);
            lastClaim.remove(player.getUniqueId());

            if (fromClaim.getFlag(ClaimFlag.LEAVE_MESSAGES)) {
                // Title
                String title    = plugin.getLangManager().get("leave-claim.title",   "name", fromClaim.getName(), "owner", fromClaim.getOwnerName());
                String subtitle = plugin.getLangManager().get("leave-claim.subtitle", "name", fromClaim.getName(), "owner", fromClaim.getOwnerName());
                player.sendTitle(title, subtitle, 5, 30, 10);

                // Broadcast custom message to players in same world
                String msg = plugin.getLangManager().get("leave-claim.custom-message",
                        "player", player.getName(), "name", fromClaim.getName());
                for (Player p : fromClaim.getTrustedPlayers().keySet().stream()
                        .map(Bukkit::getPlayer).filter(Objects::nonNull).toList()) {
                    p.sendMessage(msg);
                }
            }
        }

        // ── Entered a claim ───────────────────────────────────
        if (toClaim != null && !toClaim.equals(fromClaim)) {
            lastClaim.put(player.getUniqueId(), toClaim.getId());
            showBossBar(player, toClaim);

            if (toClaim.getFlag(ClaimFlag.JOIN_MESSAGES)) {
                String title    = plugin.getLangManager().get("enter-claim.title",    "name", toClaim.getName(), "owner", toClaim.getOwnerName());
                String subtitle = plugin.getLangManager().get("enter-claim.subtitle", "name", toClaim.getName(), "owner", toClaim.getOwnerName());
                player.sendTitle(title, subtitle, 5, 40, 10);

                String msg = plugin.getLangManager().get("enter-claim.custom-message",
                        "player", player.getName(), "name", toClaim.getName());
                for (Player p : toClaim.getTrustedPlayers().keySet().stream()
                        .map(Bukkit::getPlayer).filter(Objects::nonNull).toList()) {
                    p.sendMessage(msg);
                }
                // Notify the owner if online
                Player owner = Bukkit.getPlayer(toClaim.getOwnerUUID());
                if (owner != null && !owner.equals(player)) owner.sendMessage(msg);
            }
        }

        // ── No claim (wilderness) ─────────────────────────────
        if (toClaim == null) {
            removeBossBar(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeBossBar(event.getPlayer());
        lastClaim.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Re-create bossbar if player is inside a claim
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
            if (claim != null) showBossBar(player, claim);
        }, 5L);
    }

    // ─── BossBar helpers ──────────────────────────────────────

    private void showBossBar(Player player, Claim claim) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) return;

        removeBossBar(player);

        String colorStr = plugin.getConfig().getString("bossbar.color", "BLUE");
        String styleStr = plugin.getConfig().getString("bossbar.style", "SOLID");
        String prefix   = plugin.getConfig().getString("bossbar.prefix", "⚑ ");

        BarColor color;
        BarStyle style;
        try { color = BarColor.valueOf(colorStr); } catch (Exception e) { color = BarColor.BLUE; }
        try { style = BarStyle.valueOf(styleStr);  } catch (Exception e) { style = BarStyle.SOLID; }

        String title = ColorUtil.colorize(prefix + claim.getName()
                + " &8| &7" + claim.getOwnerName());

        BossBar bar = Bukkit.createBossBar(title, color, style);
        bar.setProgress(1.0);
        bar.addPlayer(player);

        bossBars.put(player.getUniqueId(), bar);
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    /**
     * Update the bossbar title for a player (e.g. after claim rename).
     */
    public void refreshBossBar(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim != null) showBossBar(player, claim);
        else removeBossBar(player);
    }

    /**
     * Remove all bossbars (on plugin disable / reload).
     */
    public void removeAll() {
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            entry.getValue().setVisible(false);
            entry.getValue().removeAll();
        }
        bossBars.clear();
        lastClaim.clear();
    }
}
