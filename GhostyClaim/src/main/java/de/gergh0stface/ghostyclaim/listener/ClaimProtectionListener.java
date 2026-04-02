package de.gergh0stface.ghostyclaim.listener;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimFlag;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

/**
 * Handles all claim protection events (building, TNT, fire, PvP, mobs, etc.)
 */
public class ClaimProtectionListener implements Listener {

    private final GhostyClaim plugin;

    public ClaimProtectionListener(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    // ─── Block Place / Break ──────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSessionManager().hasSession(player)) return; // session handles own blocks
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (isAdmin(player) || claim.isTrusted(player.getUniqueId())) return;
        event.setCancelled(true);
        plugin.getLangManager().send(player, "claim.no-permission-here");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSessionManager().hasSession(player)) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (isAdmin(player) || claim.isTrusted(player.getUniqueId())) return;
        event.setCancelled(true);
        plugin.getLangManager().send(player, "claim.no-permission-here");
    }

    // ─── Interaction ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSessionManager().hasSession(player)) return;
        if (event.getClickedBlock() == null) return;

        Claim claim = plugin.getClaimManager().getClaimAt(event.getClickedBlock().getLocation());
        if (claim == null) return;
        if (isAdmin(player) || claim.isTrusted(player.getUniqueId())) return;

        // Check INTERACT flag for non-trusted
        if (!claim.getFlag(ClaimFlag.INTERACT)) {
            event.setCancelled(true);
        }
    }

    // ─── Entity Damage ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Claim claim = plugin.getClaimManager().getClaimAt(victim.getLocation());
        if (claim == null) return;

        Player damager = null;
        if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player) {
            damager = (Player) proj.getShooter();
        }

        if (damager != null) {
            if (isAdmin(damager)) return;

            // PvP
            if (victim instanceof Player) {
                if (!claim.getFlag(ClaimFlag.PVP)) {
                    event.setCancelled(true);
                }
                return;
            }

            // Animal damage
            if (victim instanceof Animals) {
                if (!claim.getFlag(ClaimFlag.ANIMAL_DAMAGE)
                        && !claim.isTrusted(damager.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // ─── TNT / Explosions ─────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        Claim claim = plugin.getClaimManager().getClaimAt(entity.getLocation());
        if (claim == null) {
            // Remove blocks that land in claimed areas
            event.blockList().removeIf(block -> {
                Claim c = plugin.getClaimManager().getClaimAt(block.getLocation());
                if (c == null) return false;
                if (entity.getType() == org.bukkit.entity.EntityType.TNT_MINECART
                        || entity.getType() == org.bukkit.entity.EntityType.TNT) {
                    return !c.getFlag(ClaimFlag.TNT);
                }
                if (entity instanceof Creeper) {
                    return !c.getFlag(ClaimFlag.CREEPER_DAMAGE);
                }
                return !c.getFlag(ClaimFlag.EXPLOSIONS);
            });
            return;
        }

        // Explosion originates inside a claim
        if (entity.getType() == org.bukkit.entity.EntityType.TNT_MINECART
                || entity.getType() == org.bukkit.entity.EntityType.TNT) {
            if (!claim.getFlag(ClaimFlag.TNT)) {
                event.blockList().clear();
                event.setCancelled(false);
            }
        } else if (entity instanceof Creeper) {
            if (!claim.getFlag(ClaimFlag.CREEPER_DAMAGE)) {
                event.blockList().clear();
            }
        } else {
            if (!claim.getFlag(ClaimFlag.EXPLOSIONS)) {
                event.blockList().clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Claim c = plugin.getClaimManager().getClaimAt(block.getLocation());
            return c != null && !c.getFlag(ClaimFlag.EXPLOSIONS);
        });
    }

    // ─── Fire Spread ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() != org.bukkit.Material.FIRE) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    // ─── Mob Spawning ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (!(event.getEntity() instanceof Monster)) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    // ─── Leaf Decay ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    // ─── Ice / Snow Melt ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        org.bukkit.Material type = event.getBlock().getType();
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;

        if (type == org.bukkit.Material.ICE || type == org.bukkit.Material.FROSTED_ICE
                || type == org.bukkit.Material.PACKED_ICE) {
            if (!claim.getFlag(ClaimFlag.ICE_MELT)) event.setCancelled(true);
        } else if (type == org.bukkit.Material.SNOW || type == org.bukkit.Material.SNOW_BLOCK) {
            if (!claim.getFlag(ClaimFlag.SNOW_MELT)) event.setCancelled(true);
        }
    }

    // ─── Piston (protect against griefing from outside) ──────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation().add(event.getDirection().getDirection()));
            if (claim != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation().add(event.getDirection().getDirection()));
            if (claim != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ─── Utility ──────────────────────────────────────────────

    private boolean isAdmin(Player player) {
        return player.hasPermission("ghostyclaim.admin.bypass");
    }
}
