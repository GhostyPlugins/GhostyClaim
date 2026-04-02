package de.gergh0stface.ghostyclaim.manager;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the claim border particle visualizer AND the session guide border.
 */
public class VisualizerManager {

    private final GhostyClaim plugin;
    private final Set<UUID> activeVisualizers = new HashSet<>();
    private BukkitRunnable task;
    private BukkitRunnable guideBorderTask;

    public VisualizerManager(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    // ─── Claim visualizer ─────────────────────────────────────

    public void startTask() {
        int interval = plugin.getConfig().getInt("visualizer.refresh-interval", 10);

        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(activeVisualizers)) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        activeVisualizers.remove(uuid);
                        continue;
                    }
                    Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
                    if (claim == null) {
                        for (Claim c : plugin.getClaimManager().getClaimsForPlayer(uuid)) {
                            showBorder(player, c);
                        }
                    } else {
                        showBorder(player, claim);
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 0L, interval);
    }

    // ─── Session guide border ─────────────────────────────────

    /**
     * Start the repeating task that draws the 4×4-chunk guide border for
     * all players currently in a claim session.
     */
    public void startGuideBorderTask() {
        if (!plugin.getConfig().getBoolean("session.guide-border.enabled", true)) return;

        int interval = plugin.getConfig().getInt("session.guide-border.refresh-interval", 15);

        guideBorderTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(plugin.getSessionManager().getActiveSessions())) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;

                    var session = plugin.getSessionManager().getSession(player);
                    if (session == null || !session.hasBorderBounds()) continue;

                    drawGuideBorderBounds(player, session);
                }
            }
        };
        guideBorderTask.runTaskTimer(plugin, 5L, interval);
    }

    public void stopGuideBorderTask() {
        if (guideBorderTask != null) { guideBorderTask.cancel(); guideBorderTask = null; }
    }

    /**
     * Draw the adaptive guide border for a session.
     * The bounds are taken directly from the session so they automatically
     * reflect whether this is a new claim or an expansion.
     */
    public void drawGuideBorderBounds(Player player,
                                      de.gergh0stface.ghostyclaim.model.ClaimSession session) {
        if (!plugin.getConfig().getBoolean("session.guide-border.enabled", true)) return;
        if (!session.hasBorderBounds()) return;

        World world = player.getWorld();
        if (!world.getName().equals(session.getBorderWorld())) return;

        drawBorderRect(player, world,
                session.getBorderMinX(), session.getBorderMinZ(),
                session.getBorderMaxX(), session.getBorderMaxZ());
    }

    /**
     * @deprecated Use {@link #drawGuideBorderBounds} instead.
     */
    @Deprecated
    public void drawGuideBorder(Player player, Location center) {
        if (center == null) return;
        int half = plugin.getConfig().getInt("session.guide-border.size", 64) / 2;
        int cx = center.getBlockX(), cz = center.getBlockZ();
        drawBorderRect(player, player.getWorld(), cx - half, cz - half, cx + half, cz + half);
    }

    /** Internal: draw a rectangular particle border between two corners. */
    private void drawBorderRect(Player player, World world,
                                 int minX, int minZ, int maxX, int maxZ) {
        String pStr     = plugin.getConfig().getString("session.guide-border.particle", "DUST");
        String hexColor = plugin.getConfig().getString("session.guide-border.color", "#FFFF00");
        float  pSize    = (float) plugin.getConfig().getDouble("session.guide-border.particle-size", 1.2);
        java.util.List<Integer> offsets =
                plugin.getConfig().getIntegerList("session.guide-border.height-offsets");
        if (offsets.isEmpty()) offsets = java.util.Arrays.asList(0, 1);

        Particle particle;
        try { particle = Particle.valueOf(pStr); } catch (Exception e) { particle = Particle.DUST; }

        Color color;
        try {
            java.awt.Color awt = java.awt.Color.decode(hexColor);
            color = Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue());
        } catch (Exception e) { color = Color.YELLOW; }

        final Particle fP = particle;
        final Color    fC = color;
        final float    fS = pSize;
        double playerY = player.getLocation().getY();

        for (int yOff : offsets) {
            double y = playerY + yOff;
            for (int x = minX; x <= maxX; x += 2) {
                spawnParticle(player, world, fP, fC, fS, x + 0.5, y, minZ);
                spawnParticle(player, world, fP, fC, fS, x + 0.5, y, maxZ + 1);
            }
            for (int z = minZ; z <= maxZ; z += 2) {
                spawnParticle(player, world, fP, fC, fS, minX,     y, z + 0.5);
                spawnParticle(player, world, fP, fC, fS, maxX + 1, y, z + 0.5);
            }
        }
    }

    public void stopTask() {
        if (task != null) { task.cancel(); task = null; }
        stopGuideBorderTask();
    }

    public boolean isActive(Player player) {
        return activeVisualizers.contains(player.getUniqueId());
    }

    public void toggle(Player player) {
        if (isActive(player)) {
            activeVisualizers.remove(player.getUniqueId());
        } else {
            activeVisualizers.add(player.getUniqueId());
        }
    }

    public void disable(Player player) {
        activeVisualizers.remove(player.getUniqueId());
    }

    // ─── Particle rendering ───────────────────────────────────

    private void showBorder(Player player, Claim claim) {
        World world = player.getWorld();
        if (!world.getName().equals(claim.getWorldName())) return;

        String particleStr = plugin.getConfig().getString("visualizer.particle", "DUST");
        String colorHex = plugin.getConfig().getString("visualizer.color", "#FF0000");
        float size = (float) plugin.getConfig().getDouble("visualizer.size", 1.5);

        Particle particle;
        try {
            particle = Particle.valueOf(particleStr);
        } catch (IllegalArgumentException e) {
            particle = Particle.DUST;
        }

        Color color;
        try {
            java.awt.Color awt = java.awt.Color.decode(colorHex);
            color = Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue());
        } catch (Exception e) {
            color = Color.RED;
        }

        int density = plugin.getConfig().getInt("visualizer.density", 1);
        double playerY = player.getLocation().getY();

        // Height offsets for vertical particles
        java.util.List<Integer> offsets = plugin.getConfig().getIntegerList("visualizer.height-offsets");
        if (offsets.isEmpty()) { offsets = java.util.Arrays.asList(0, 1, 2); }

        int minX = claim.getMinX();
        int maxX = claim.getMaxX();
        int minZ = claim.getMinZ();
        int maxZ = claim.getMaxZ();

        final Particle finalParticle = particle;
        final Color finalColor = color;
        final float finalSize = size;

        // Draw all 4 sides of the border
        for (int offset : offsets) {
            double y = playerY + offset;

            // North (minZ) and South (maxZ) walls
            for (int x = minX; x <= maxX; x += density) {
                spawnParticle(player, world, finalParticle, finalColor, finalSize, x + 0.5, y, minZ);
                spawnParticle(player, world, finalParticle, finalColor, finalSize, x + 0.5, y, maxZ + 1);
            }

            // West (minX) and East (maxX) walls
            for (int z = minZ; z <= maxZ; z += density) {
                spawnParticle(player, world, finalParticle, finalColor, finalSize, minX, y, z + 0.5);
                spawnParticle(player, world, finalParticle, finalColor, finalSize, maxX + 1, y, z + 0.5);
            }
        }
    }

    /**
     * Show a temporary preview for a pending claim area (during session).
     */
    public void showPreview(Player player, int minX, int minZ, int maxX, int maxZ) {
        World world = player.getWorld();
        double y = player.getLocation().getY();
        Color previewColor = Color.YELLOW;

        for (int x = minX; x <= maxX; x++) {
            spawnParticle(player, world, Particle.DUST, previewColor, 1.5f, x + 0.5, y, minZ);
            spawnParticle(player, world, Particle.DUST, previewColor, 1.5f, x + 0.5, y, maxZ + 1);
        }
        for (int z = minZ; z <= maxZ; z++) {
            spawnParticle(player, world, Particle.DUST, previewColor, 1.5f, minX, y, z + 0.5);
            spawnParticle(player, world, Particle.DUST, previewColor, 1.5f, maxX + 1, y, z + 0.5);
        }
    }

    private void spawnParticle(Player player, World world, Particle particle,
                               Color color, float size, double x, double y, double z) {
        // Only show to players within 64 blocks
        Location loc = new Location(world, x, y, z);
        if (player.getLocation().distanceSquared(loc) > 64 * 64) return;

        if (particle == Particle.DUST) {
            player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, size));
        } else {
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }
}
