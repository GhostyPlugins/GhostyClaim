package de.gergh0stface.ghostyclaim.manager;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.ClaimSession;
import de.gergh0stface.ghostyclaim.util.ColorUtil;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Manages active claim creation sessions.
 */
public class SessionManager {

    private final GhostyClaim plugin;
    private final Map<UUID, ClaimSession> sessions = new HashMap<>();
    private BukkitRunnable timeoutTask;

    // Slot assignments for session items
    public static final int TORCH_SLOT = 0;
    public static final int CONFIRM_SLOT = 7;
    public static final int CANCEL_SLOT = 8;

    public SessionManager(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a claim session for a player.
     */
    public ClaimSession startSession(Player player) {
        return startSession(player, false, null);
    }

    /**
     * Start an expansion session for an existing claim.
     */
    public ClaimSession startExpansionSession(Player player, de.gergh0stface.ghostyclaim.model.Claim claim) {
        ClaimSession session = startSession(player, true, claim.getId());
        return session;
    }

    private ClaimSession startSession(Player player, boolean expansion,
                                      java.util.UUID expandingClaimId) {
        ClaimSession session = new ClaimSession(player.getUniqueId());

        // Mark as expansion if applicable
        if (expansion && expandingClaimId != null) {
            session.setExpansion(expandingClaimId);
        }

        // Save current state
        session.setSavedInventory(player.getInventory().getContents().clone());
        session.setSavedGameMode(player.getGameMode());
        session.setWasFlying(player.isFlying());
        session.setSavedLocation(player.getLocation().clone());

        sessions.put(player.getUniqueId(), session);

        // ── Compute adaptive guide border bounds ──────────────
        computeBorderBounds(session, player, expansion, expandingClaimId);

        // Set to creative-like fly
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }

        // Give session items
        giveSessionItems(player, session);

        // Draw the guide border immediately on the next tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            plugin.getVisualizerManager().drawGuideBorderBounds(player, session), 2L);

        return session;
    }

    /**
     * Compute the guide border bounds for a session and store them on the session object.
     *
     * NEW CLAIM  → square of config size (default 64) centred on the player.
     * EXPANSION  → existing claim bounds + a buffer on each side.
     *   buffer = max( configSize/2 , 32 )  so tiny claims still get a useful workspace.
     */
    private void computeBorderBounds(ClaimSession session, Player player,
                                     boolean expansion, java.util.UUID expandingClaimId) {
        String world = player.getWorld().getName();

        if (expansion && expandingClaimId != null) {
            // ── Expansion: size based on current claim ────────
            de.gergh0stface.ghostyclaim.model.Claim claim =
                    plugin.getClaimManager().getClaim(expandingClaimId);

            if (claim != null) {
                int configHalf = plugin.getConfig().getInt("session.guide-border.size", 64) / 2;
                // Buffer = at least 32 blocks each side, or half the config size — whichever is larger
                int buffer = Math.max(32, configHalf);

                session.setBorderBounds(
                        claim.getMinX() - buffer,
                        claim.getMinZ() - buffer,
                        claim.getMaxX() + buffer,
                        claim.getMaxZ() + buffer,
                        world);
                return;
            }
        }

        // ── New claim: fixed config size centred on player ────
        int half = plugin.getConfig().getInt("session.guide-border.size", 64) / 2;
        int cx   = player.getLocation().getBlockX();
        int cz   = player.getLocation().getBlockZ();
        session.setBorderBounds(cx - half, cz - half, cx + half, cz + half, world);
    }

    /**
     * End a session (cancelled or completed), restoring the player's state.
     */
    public void endSession(Player player) {
        ClaimSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;

        // Clear hotbar and restore inventory
        player.getInventory().clear();
        player.getInventory().setContents(session.getSavedInventory());

        // Restore game mode / fly
        if (session.getSavedGameMode() != GameMode.CREATIVE && session.getSavedGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }

        // Remove placed torches from world.
        // We set AIR directly — the torch may appear as REDSTONE_TORCH even
        // when submerged because BlockPhysicsEvent was cancelled to keep it in place.
        for (var loc : session.getTorchLocations()) {
            Block b = loc.getBlock();
            if (b.getType() == Material.REDSTONE_TORCH
                    || b.getType() == Material.REDSTONE_WALL_TORCH) {
                b.setType(Material.AIR);
            }
        }

        player.updateInventory();
    }

    /**
     * Get the active session for a player, or null.
     */
    public ClaimSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Returns a snapshot of all UUIDs with active sessions (for border rendering). */
    public java.util.Set<UUID> getActiveSessions() {
        return java.util.Collections.unmodifiableSet(sessions.keySet());
    }

    /**
     * Update the torch item display in the player's inventory.
     */
    public void updateTorchItem(Player player) {
        ClaimSession session = getSession(player);
        if (session == null) return;

        int count = session.getTorchCount();
        LangManager lang = plugin.getLangManager();

        List<String> lore = lang.getList("session.torch-item-lore",
                "count", count);

        ItemStack torch = new ItemBuilder(Material.REDSTONE_TORCH, Math.max(1, 8 - count))
                .name(lang.get("session.torch-item-name", "count", count))
                .lore(lore)
                .build();

        player.getInventory().setItem(TORCH_SLOT, torch);
        player.updateInventory();
    }

    /**
     * Start the session timeout + countdown task.
     * Runs every second; shows actionbar countdown, title in last N seconds,
     * plays warning sounds, and ends the session when time is up.
     */
    public void startTimeoutTask() {
        int timeout = plugin.getConfig().getInt("session.timeout", 90);
        if (timeout <= 0) return;

        final List<Integer> warnAt = plugin.getConfig().getIntegerList("session.warn-at-seconds");
        final int countdownTitleSecs = plugin.getConfig().getInt("session.countdown-title-seconds", 5);

        timeoutTask = new BukkitRunnable() {
            // Copy finals into local fields so all IDEs recognise usage
            private final List<Integer> _warnAt = warnAt;
            private final int _countdownSecs     = countdownTitleSecs;

            @Override
            public void run() {
                List<UUID> toRemove = new ArrayList<>();

                for (Map.Entry<UUID, ClaimSession> entry : new java.util.HashMap<>(sessions).entrySet()) {
                    ClaimSession session = entry.getValue();
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player == null) { toRemove.add(entry.getKey()); continue; }

                    long elapsed   = session.getElapsedSeconds();
                    long remaining = timeout - elapsed;

                    // ── Kick when time is up ──────────────────────
                    if (remaining <= 0) {
                        toRemove.add(entry.getKey());
                        continue;
                    }

                    // ── Actionbar countdown (always) ──────────────
                    String bar = buildActionBar(player, remaining, timeout);
                    // Use BungeeCord chat API — available on all Spigot/Paper 1.21 builds
                    player.spigot().sendMessage(
                            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(
                                    ColorUtil.colorize(bar)));

                    // ── Big title countdown in last N seconds ─────
                    if (remaining <= _countdownSecs) {
                        String titleText = plugin.getLangManager().get(
                                "session.countdown-title", "seconds", remaining);
                        String subText   = plugin.getLangManager().get(
                                "session.countdown-subtitle");
                        player.sendTitle(titleText, subText, 0, 25, 5);
                    }

                    // ── Warning sounds at thresholds ──────────────
                    if (_warnAt.contains((int) remaining)) {
                        if (remaining <= 5) {
                            player.playSound(player.getLocation(),
                                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f,
                                    0.5f + (remaining * 0.1f));
                        } else {
                            player.playSound(player.getLocation(),
                                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                        }
                        // Send chat warning at 60 / 30 / 10 s
                        if (remaining == 60 || remaining == 30 || remaining == 10) {
                            plugin.getLangManager().send(player, "session.timeout-warning",
                                    "seconds", remaining);
                        }
                    }
                }

                // End timed-out sessions
                for (UUID uuid : toRemove) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null) {
                        endSession(player);
                        plugin.getLangManager().send(player, "session.timeout");
                        player.sendTitle(
                                plugin.getLangManager().get("session.timeout-title"),
                                plugin.getLangManager().get("session.timeout-subtitle"),
                                5, 60, 20);
                        player.playSound(player.getLocation(),
                                org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    } else {
                        sessions.remove(uuid);
                    }
                }
            }
        };
        timeoutTask.runTaskTimer(plugin, 20L, 20L); // every second
    }

    /** Build the colour-coded actionbar progress bar. */
    private String buildActionBar(Player player, long remaining, int total) {
        int barLength = 20;
        int filled = (int) Math.round((double) remaining / total * barLength);
        filled = Math.max(0, Math.min(barLength, filled));

        String filledColor  = remaining > 30 ? "&a" : remaining > 10 ? "&e" : "&c";
        String emptyColor   = "&8";
        String bar = filledColor + "█".repeat(filled)
                   + emptyColor + "█".repeat(barLength - filled);

        return plugin.getLangManager().get("session.actionbar",
                "bar", bar,
                "seconds", remaining,
                "total", total);
    }

    public void stopTimeoutTask() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    /**
     * Cancel all active sessions (on plugin disable).
     */
    public void cancelAll() {
        for (UUID uuid : new HashSet<>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                endSession(player);
            } else {
                sessions.remove(uuid);
            }
        }
    }

    // ─── Private helpers ──────────────────────────────────────

    private void giveSessionItems(Player player, ClaimSession session) {
        player.getInventory().clear();

        LangManager lang = plugin.getLangManager();
        boolean isExpansion = session.isExpansion();

        // Slot 0: 8 Redstone Torches
        List<String> torchLore = lang.getList("session.torch-item-lore", "count", 0);
        String torchName = isExpansion
                ? lang.get("expansion.torch-item-name", "count", 0)
                : lang.get("session.torch-item-name", "count", 0);
        ItemStack torches = new ItemBuilder(Material.REDSTONE_TORCH, 8)
                .name(torchName)
                .lore(torchLore)
                .build();
        player.getInventory().setItem(TORCH_SLOT, torches);

        // Slot 1: Info item (expansion mode only) or filler
        if (isExpansion) {
            de.gergh0stface.ghostyclaim.model.Claim expandClaim =
                    plugin.getClaimManager().getClaim(session.getExpandingClaimId());
            String claimName = expandClaim != null ? expandClaim.getName() : "?";
            player.getInventory().setItem(1,
                    new ItemBuilder(Material.MAP)
                            .name(lang.get("expansion.info-item-name"))
                            .lore(lang.getList("expansion.info-item-lore",
                                    "name",    claimName,
                                    "area",    expandClaim != null ? expandClaim.getArea() : 0,
                                    "minX",    expandClaim != null ? expandClaim.getMinX() : 0,
                                    "minZ",    expandClaim != null ? expandClaim.getMinZ() : 0,
                                    "maxX",    expandClaim != null ? expandClaim.getMaxX() : 0,
                                    "maxZ",    expandClaim != null ? expandClaim.getMaxZ() : 0))
                            .build());
        }

        // Slots 2-6: Filler
        ItemStack filler = ItemBuilder.filler();
        for (int i = (isExpansion ? 2 : 1); i <= 6; i++) {
            player.getInventory().setItem(i, filler);
        }

        // Slot 7: Confirm
        List<String> confirmLore = lang.getList("session.confirm-item-lore");
        String confirmName = isExpansion
                ? lang.get("expansion.confirm-item-name")
                : lang.get("session.confirm-item-name");
        ItemStack confirm = new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
                .name(confirmName)
                .lore(confirmLore)
                .glow()
                .build();
        player.getInventory().setItem(CONFIRM_SLOT, confirm);

        // Slot 8: Cancel
        List<String> cancelLore = lang.getList("session.cancel-item-lore");
        ItemStack cancel = new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(lang.get("session.cancel-item-name"))
                .lore(cancelLore)
                .build();
        player.getInventory().setItem(CANCEL_SLOT, cancel);

        player.updateInventory();
    }

    /**
     * Check if an ItemStack is the confirm session item.
     */
    public boolean isConfirmItem(ItemStack item) {
        if (item == null || item.getType() != Material.GREEN_STAINED_GLASS_PANE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return ColorUtil.strip(item.getItemMeta().getDisplayName()).contains("Confirm") ||
               ColorUtil.strip(item.getItemMeta().getDisplayName()).contains("Bestätigen");
    }

    /**
     * Check if an ItemStack is the cancel session item.
     */
    public boolean isCancelItem(ItemStack item) {
        if (item == null || item.getType() != Material.RED_STAINED_GLASS_PANE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return ColorUtil.strip(item.getItemMeta().getDisplayName()).contains("Cancel") ||
               ColorUtil.strip(item.getItemMeta().getDisplayName()).contains("Abbrechen");
    }
}
