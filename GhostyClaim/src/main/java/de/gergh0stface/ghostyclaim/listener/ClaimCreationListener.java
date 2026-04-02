package de.gergh0stface.ghostyclaim.listener;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimSession;
import de.gergh0stface.ghostyclaim.manager.SessionManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles all events related to claim creation sessions.
 */
public class ClaimCreationListener implements Listener {

    private final GhostyClaim plugin;

    public ClaimCreationListener(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    // ─── Block Place (torch detection + session protection) ──────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSessionManager().hasSession(player)) return;

        ClaimSession session = plugin.getSessionManager().getSession(player);
        Block block = event.getBlockPlaced();

        // Only allow placing redstone torches during session
        if (block.getType() != Material.REDSTONE_TORCH) {
            event.setCancelled(true);
            return;
        }

        // Check if world is allowed
        if (!plugin.getClaimManager().isWorldAllowed(block.getWorld().getName())) {
            event.setCancelled(true);
            plugin.getLangManager().send(player, "session.torch-outside-world");
            return;
        }

        // Don't allow more than 4
        if (session.getTorchCount() >= ClaimSession.REQUIRED_TORCHES) {
            event.setCancelled(true);
            return;
        }

        // Check if torch location is inside an existing claim
        Claim existing = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (existing != null && !existing.getOwnerUUID().equals(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getLangManager().send(player, "session.torch-in-claim");
            return;
        }

        session.addTorch(block.getLocation());
        plugin.getSessionManager().updateTorchItem(player);
        plugin.getLangManager().send(player, "session.torch-placed", "count", session.getTorchCount());

        // If all 4 placed, show preview
        if (session.hasAllTorches()) {
            int[] bounds = session.calculateBounds();
            if (bounds != null) {
                int area = (bounds[2] - bounds[0] + 1) * (bounds[3] - bounds[1] + 1);
                double cost = plugin.getEconomyManager().calculateClaimCost(area);
                plugin.getLangManager().send(player, "session.confirm-prompt",
                        "area", area,
                        "cost", plugin.getEconomyManager().format(cost),
                        "minX", bounds[0], "minZ", bounds[1],
                        "maxX", bounds[2], "maxZ", bounds[3]);

                // Show particle preview
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getVisualizerManager().showPreview(player, bounds[0], bounds[1], bounds[2], bounds[3]),
                2L);
            }
        }
    }

    // ─── Block Break (torch removal + session protection) ────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSessionManager().hasSession(player)) return;

        Block block = event.getBlock();
        ClaimSession session = plugin.getSessionManager().getSession(player);

        // Only allow breaking own claim torches - cancel everything else
        if (block.getType() == Material.REDSTONE_TORCH
                && session.removeTorch(block.getLocation())) {
            plugin.getSessionManager().updateTorchItem(player);
            plugin.getLangManager().send(player, "session.torch-removed",
                    "count", session.getTorchCount());
            // Allow the break
            return;
        }

        // Block all other digging during session
        event.setCancelled(true);
    }

    // ─── Player Interact (confirm/cancel items) ───────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSessionManager().hasSession(player)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        SessionManager sm = plugin.getSessionManager();

        if (sm.isConfirmItem(item)) {
            event.setCancelled(true);
            handleConfirm(player);
        } else if (sm.isCancelItem(item)) {
            event.setCancelled(true);
            handleCancel(player);
        }
    }

    // ─── Prevent inventory manipulation during session ────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.getSessionManager().hasSession(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (plugin.getSessionManager().hasSession(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSessionManager().hasSession(player)) {
            plugin.getSessionManager().endSession(player);
        }
    }

    // ─── Physics: prevent water from destroying session torches ──
    //
    // When water flows into a redstone torch Minecraft fires
    // BlockPhysicsEvent and would destroy the torch. We cancel
    // the event so the torch stays in place and water can be claimed.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.REDSTONE_TORCH) return;

        // Check if this torch belongs to any active session
        org.bukkit.Location loc = block.getLocation();
        for (UUID uuid : plugin.getSessionManager().getActiveSessions()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;

            ClaimSession session = plugin.getSessionManager().getSession(player);
            if (session == null) continue;

            // If this block is a tracked torch, cancel the physics destruction
            for (org.bukkit.Location torchLoc : session.getTorchLocations()) {
                if (torchLoc.getBlockX() == loc.getBlockX()
                        && torchLoc.getBlockY() == loc.getBlockY()
                        && torchLoc.getBlockZ() == loc.getBlockZ()
                        && torchLoc.getWorld() != null
                        && torchLoc.getWorld().equals(loc.getWorld())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ─── Confirm Logic ────────────────────────────────────────

    private void handleConfirm(Player player) {
        ClaimSession session = plugin.getSessionManager().getSession(player);
        if (session == null) return;

        if (!session.hasAllTorches()) {
            plugin.getLangManager().send(player, "session.need-four-torches");
            return;
        }

        int[] bounds = session.calculateBounds();
        if (bounds == null) return;

        int minX = bounds[0], minZ = bounds[1], maxX = bounds[2], maxZ = bounds[3];
        String world = session.getWorldName();
        if (world == null) { plugin.getLangManager().send(player, "general.invalid-usage"); return; }

        int area  = (maxX - minX + 1) * (maxZ - minZ + 1);
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int minDim  = plugin.getClaimManager().getMinDimension();
        int minArea = plugin.getClaimManager().getMinArea();
        int maxArea = plugin.getClaimManager().getMaxArea();

        // Dimension validation
        if (width < minDim || length < minDim) {
            plugin.getLangManager().send(player, "session.dimension-too-small", "min", minDim); return;
        }
        if (area < minArea) {
            plugin.getLangManager().send(player, "session.area-too-small", "min", minArea, "actual", area); return;
        }
        if (area > maxArea) {
            plugin.getLangManager().send(player, "session.area-too-large", "max", maxArea, "actual", area); return;
        }

        // ── EXPANSION branch ──────────────────────────────────
        if (session.isExpansion()) {
            handleExpansionConfirm(player, session, world, minX, minZ, maxX, maxZ, area);
            return;
        }

        // ── NEW CLAIM branch ──────────────────────────────────
        int maxClaims = plugin.getClaimManager().getMaxClaimsForPlayer(player);
        if (maxClaims > 0 && plugin.getClaimManager().getClaimCount(player.getUniqueId()) >= maxClaims) {
            plugin.getLangManager().send(player, "claim.max-reached", "max", maxClaims); return;
        }

        Claim overlap = plugin.getClaimManager().checkOverlap(world, minX, minZ, maxX, maxZ);
        if (overlap != null) { plugin.getLangManager().send(player, "session.overlaps-existing"); return; }

        double cost = plugin.getEconomyManager().calculateClaimCost(area);
        if (plugin.getEconomyManager().isEnabled()) {
            if (!plugin.getEconomyManager().canAfford(player, cost)) {
                plugin.getLangManager().send(player, "economy.not-enough-money",
                        "amount",  plugin.getEconomyManager().format(cost),
                        "balance", plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)));
                return;
            }
        }

        plugin.getSessionManager().endSession(player);

        if (plugin.getEconomyManager().isEnabled()) {
            plugin.getEconomyManager().charge(player, cost);
            plugin.getLangManager().send(player, "economy.charge-message",
                    "amount", plugin.getEconomyManager().format(cost));
        }

        String defaultName = plugin.getLangManager().get("claim.default-name", "player", player.getName());
        Claim claim = new Claim(UUID.randomUUID(), player.getUniqueId(), player.getName(),
                world, minX, minZ, maxX, maxZ, defaultName);
        plugin.getClaimManager().addClaim(claim);
        plugin.getLangManager().send(player, "claim.created", "name", claim.getName());

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 20, 1, 1, 1);
    }

    // ─── Expansion Confirm Logic ──────────────────────────────

    private void handleExpansionConfirm(Player player, ClaimSession session,
                                        String world, int minX, int minZ, int maxX, int maxZ, int newArea) {
        UUID claimId = session.getExpandingClaimId();
        Claim claim  = plugin.getClaimManager().getClaim(claimId);
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found");
            plugin.getSessionManager().endSession(player);
            return;
        }

        int oldArea  = claim.getArea();

        // New bounds must fully enclose the original claim
        if (minX > claim.getMinX() || minZ > claim.getMinZ()
                || maxX < claim.getMaxX() || maxZ < claim.getMaxZ()) {
            plugin.getLangManager().send(player, "expansion.must-contain-original");
            return;
        }

        // Overlap check (excluding this claim itself)
        Claim overlap = plugin.getClaimManager().checkOverlap(world, minX, minZ, maxX, maxZ, claimId);
        if (overlap != null) {
            plugin.getLangManager().send(player, "session.overlaps-existing"); return;
        }

        // Economy: charge only the difference
        double oldCost  = plugin.getEconomyManager().calculateClaimCost(oldArea);
        double newCost  = plugin.getEconomyManager().calculateClaimCost(newArea);
        double diffCost = Math.max(0, newCost - oldCost);

        if (plugin.getEconomyManager().isEnabled() && diffCost > 0) {
            if (!plugin.getEconomyManager().canAfford(player, diffCost)) {
                plugin.getLangManager().send(player, "economy.not-enough-money",
                        "amount",  plugin.getEconomyManager().format(diffCost),
                        "balance", plugin.getEconomyManager().format(
                                plugin.getEconomyManager().getBalance(player)));
                return;
            }
        }

        // Apply
        plugin.getSessionManager().endSession(player);

        claim.setMinX(minX); claim.setMinZ(minZ);
        claim.setMaxX(maxX); claim.setMaxZ(maxZ);
        plugin.getClaimManager().updateClaim(claim);

        if (plugin.getEconomyManager().isEnabled() && diffCost > 0) {
            plugin.getEconomyManager().charge(player, diffCost);
            plugin.getLangManager().send(player, "economy.charge-message",
                    "amount", plugin.getEconomyManager().format(diffCost));
        }

        plugin.getLangManager().send(player, "expansion.success",
                "name",    claim.getName(),
                "oldArea", oldArea,
                "newArea", newArea);

        // Refresh bossbar
        plugin.getPlayerMoveListener().refreshBossBar(player);

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 30, 1.5, 1, 1.5);
    }

    // ─── Cancel Logic ─────────────────────────────────────────

    private void handleCancel(Player player) {
        plugin.getSessionManager().endSession(player);
        plugin.getLangManager().send(player, "session.cancelled");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }
}
