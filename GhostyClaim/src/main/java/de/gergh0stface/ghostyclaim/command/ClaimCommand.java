package de.gergh0stface.ghostyclaim.command;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.gui.*;
import de.gergh0stface.ghostyclaim.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles the /claim command and all its sub-commands.
 *
 * Subcommands:
 *   /claim                 → Start claim session
 *   /claim menu            → Open main GUI
 *   /claim list            → Open list GUI
 *   /claim cancel          → Cancel active session
 *   /claim delete          → Delete claim you're standing in
 *   /claim trust <player>  → Trust a player
 *   /claim untrust <player>→ Untrust a player
 *   /claim flags           → Open flags GUI
 *   /claim rename <name>   → Rename claim
 *   /claim visualize       → Toggle visualizer
 *   /claim info            → Show claim info
 *   /claim help            → Show help
 */
public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final GhostyClaim plugin;

    public ClaimCommand(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            plugin.getLangManager().send(null, "general.player-only");
            sender.sendMessage(plugin.getLangManager().get("general.player-only"));
            return true;
        }

        if (!player.hasPermission("ghostyclaim.use")) {
            plugin.getLangManager().send(player, "general.no-permission");
            return true;
        }

        // No args → start session or show help
        if (args.length == 0) {
            handleStartSession(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "menu", "m"          -> handleMenu(player);
            case "list", "l"          -> handleList(player);
            case "cancel", "abbrechen"-> handleCancel(player);
            case "delete", "del"      -> handleDelete(player);
            case "trust"              -> handleTrust(player, args);
            case "untrust"            -> handleUntrust(player, args);
            case "flags", "flag"      -> handleFlags(player);
            case "rename"             -> handleRename(player, args);
            case "visualize", "vis"   -> handleVisualize(player);
            case "info"               -> handleInfo(player);
            case "expand"             -> handleExpandCommand(player);
            case "admin", "a"         -> handleAdmin(player, args);
            case "help", "?"          -> handleHelp(player);
            default                   -> plugin.getLangManager().send(player, "general.invalid-usage");
        }
        return true;
    }

    // ─── Start Session / Expand ───────────────────────────────

    private void handleStartSession(Player player) {
        if (!player.hasPermission("ghostyclaim.claim.create")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }
        if (plugin.getSessionManager().hasSession(player)) {
            plugin.getLangManager().send(player, "session.already-active"); return;
        }

        String world = player.getWorld().getName();
        if (!plugin.getClaimManager().isWorldAllowed(world)) {
            plugin.getLangManager().send(player, "session.torch-outside-world"); return;
        }

        // ── Check if standing in own claim → offer expansion ──
        Claim existingClaim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (existingClaim != null && existingClaim.getOwnerUUID().equals(player.getUniqueId())) {
            handleStartExpansion(player, existingClaim);
            return;
        }

        // ── New claim: check max limit (LuckPerms-aware) ──────
        int maxClaims = plugin.getClaimManager().getMaxClaimsForPlayer(player);
        if (maxClaims > 0 && plugin.getClaimManager().getClaimCount(player.getUniqueId()) >= maxClaims) {
            plugin.getLangManager().send(player, "claim.max-reached", "max", maxClaims); return;
        }

        plugin.getSessionManager().startSession(player);
        plugin.getLangManager().send(player, "session.started");

        // Guide border hint
        int borderSize = plugin.getConfig().getInt("session.guide-border.size", 64);
        plugin.getLangManager().send(player, "session.guide-border-hint", "size", borderSize);

        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
    }

    private void handleStartExpansion(Player player, Claim claim) {
        if (!player.hasPermission("ghostyclaim.claim.expand")) {
            // No expand perm → just show menu
            new de.gergh0stface.ghostyclaim.gui.ClaimMainGui(plugin).open(player, claim);
            return;
        }
        plugin.getSessionManager().startExpansionSession(player, claim);
        plugin.getLangManager().send(player, "expansion.started",
                "name", claim.getName(),
                "area", claim.getArea(),
                "minX", claim.getMinX(), "minZ", claim.getMinZ(),
                "maxX", claim.getMaxX(), "maxZ", claim.getMaxZ());
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
    }

    // ─── Menu ─────────────────────────────────────────────────

    private void handleMenu(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            // Open list if not standing in a claim
            new ClaimListGui(plugin).open(player, 0);
            return;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ghostyclaim.admin")) {
            plugin.getLangManager().send(player, "claim.not-owner"); return;
        }
        new ClaimMainGui(plugin).open(player, claim);
    }

    // ─── List ─────────────────────────────────────────────────

    private void handleList(Player player) {
        new ClaimListGui(plugin).open(player, 0);
    }

    // ─── Cancel ───────────────────────────────────────────────

    private void handleCancel(Player player) {
        if (!plugin.getSessionManager().hasSession(player)) {
            plugin.getLangManager().send(player, "general.invalid-usage"); return;
        }
        plugin.getSessionManager().endSession(player);
        plugin.getLangManager().send(player, "session.cancelled");
    }

    // ─── Delete ───────────────────────────────────────────────

    private void handleDelete(Player player) {
        if (!player.hasPermission("ghostyclaim.claim.delete")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found"); return;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ghostyclaim.admin.deleteclaim")) {
            plugin.getLangManager().send(player, "claim.not-owner"); return;
        }
        new DeleteConfirmGui(plugin).open(player, claim);
    }

    // ─── Trust ────────────────────────────────────────────────

    private void handleTrust(Player player, String[] args) {
        if (!player.hasPermission("ghostyclaim.claim.trust")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }
        if (args.length < 2) {
            plugin.getLangManager().send(player, "general.invalid-usage"); return;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found"); return;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ghostyclaim.admin")) {
            plugin.getLangManager().send(player, "claim.not-owner"); return;
        }

        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            plugin.getLangManager().send(player, "trust.cannot-trust-self"); return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getLangManager().send(player, "general.player-not-found", "player", targetName); return;
        }

        UUID targetUUID = target.getUniqueId();

        if (claim.getOwnerUUID().equals(targetUUID)) {
            plugin.getLangManager().send(player, "trust.is-owner", "player", targetName); return;
        }
        if (claim.isTrusted(targetUUID)) {
            plugin.getLangManager().send(player, "trust.already-trusted", "player", targetName); return;
        }

        claim.addTrusted(targetUUID, target.getName() != null ? target.getName() : targetName);
        plugin.getClaimManager().updateClaim(claim);
        plugin.getLangManager().send(player, "trust.trusted", "player", targetName);
    }

    // ─── Untrust ──────────────────────────────────────────────

    private void handleUntrust(Player player, String[] args) {
        if (!player.hasPermission("ghostyclaim.claim.trust")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }
        if (args.length < 2) {
            plugin.getLangManager().send(player, "general.invalid-usage"); return;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found"); return;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ghostyclaim.admin")) {
            plugin.getLangManager().send(player, "claim.not-owner"); return;
        }

        String targetName = args[1];
        UUID targetUUID = null;
        for (Map.Entry<UUID, String> entry : claim.getTrustedPlayers().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                targetUUID = entry.getKey();
                break;
            }
        }

        if (targetUUID == null) {
            plugin.getLangManager().send(player, "trust.not-trusted", "player", targetName); return;
        }

        claim.removeTrusted(targetUUID);
        plugin.getClaimManager().updateClaim(claim);
        plugin.getLangManager().send(player, "trust.untrusted", "player", targetName);
    }

    // ─── Flags ────────────────────────────────────────────────

    private void handleFlags(Player player) {
        if (!player.hasPermission("ghostyclaim.claim.flags")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found"); return;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ghostyclaim.admin")) {
            plugin.getLangManager().send(player, "claim.not-owner"); return;
        }
        new ClaimFlagsGui(plugin).open(player, claim);
    }

    // ─── Rename ───────────────────────────────────────────────

    private void handleRename(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getLangManager().send(player, "general.invalid-usage"); return;
        }
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found"); return;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ghostyclaim.admin")) {
            plugin.getLangManager().send(player, "claim.not-owner"); return;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        int maxLen = 32;
        if (newName.length() > maxLen) {
            plugin.getLangManager().send(player, "claim.name-too-long", "max", maxLen); return;
        }

        claim.setName(newName);
        plugin.getClaimManager().updateClaim(claim);
        plugin.getLangManager().send(player, "claim.renamed", "name", newName);
        plugin.getPlayerMoveListener().refreshBossBar(player);
    }

    // ─── Visualize ────────────────────────────────────────────

    private void handleVisualize(Player player) {
        if (!player.hasPermission("ghostyclaim.claim.visualize")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }
        plugin.getVisualizerManager().toggle(player);
        if (plugin.getVisualizerManager().isActive(player)) {
            plugin.getLangManager().send(player, "visualizer.enabled");
        } else {
            plugin.getLangManager().send(player, "visualizer.disabled");
        }
    }

    // ─── Info ─────────────────────────────────────────────────

    private void handleInfo(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found"); return;
        }
        String trustedStr = claim.getTrustedPlayers().isEmpty()
                ? "None" : String.join(", ", claim.getTrustedPlayers().values());

        plugin.getLangManager().send(player, "claim.info",
                "owner",  claim.getOwnerName(),
                "name",   claim.getName(),
                "area",   claim.getArea(),
                "minX",   claim.getMinX(),
                "minZ",   claim.getMinZ(),
                "maxX",   claim.getMaxX(),
                "maxZ",   claim.getMaxZ(),
                "trusted",trustedStr);
    }

    // ─── Explicit Expand Command ──────────────────────────────

    private void handleExpandCommand(Player player) {
        if (!player.hasPermission("ghostyclaim.claim.expand")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }
        if (plugin.getSessionManager().hasSession(player)) {
            plugin.getLangManager().send(player, "session.already-active"); return;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getLangManager().send(player, "claim.not-found"); return;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ghostyclaim.admin")) {
            plugin.getLangManager().send(player, "claim.not-owner"); return;
        }

        handleStartExpansion(player, claim);
    }

    // ─── Admin GUI ────────────────────────────────────────────

    /**
     * /claim admin [player]  — Opens the admin claim management GUI.
     * Optional [player] argument pre-filters the list by owner name.
     *
     * Permissions:
     *   ghostyclaim.admin      — required
     *   ghostyclaim.admin.gui  — specific node (falls back to admin)
     */
    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("ghostyclaim.admin")
                && !player.hasPermission("ghostyclaim.admin.gui")) {
            plugin.getLangManager().send(player, "general.no-permission"); return;
        }

        String filter = (args.length >= 2) ? args[1] : null;
        new de.gergh0stface.ghostyclaim.gui.AdminClaimListGui(plugin).open(player, 0, filter);
    }

    // ─── Help ─────────────────────────────────────────────────

    private void handleHelp(Player player) {
        player.sendMessage(plugin.getLangManager().get("help.header"));
        for (String line : plugin.getLangManager().getList("help.commands")) {
            player.sendMessage(line);
        }
        player.sendMessage(plugin.getLangManager().get("help.footer"));
    }

    // ─── Tab Complete ─────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "menu", "list", "cancel", "delete", "trust", "untrust",
                    "flags", "rename", "visualize", "info", "expand", "help"
            ));
            if (player.hasPermission("ghostyclaim.admin") || player.hasPermission("ghostyclaim.admin.gui")) {
                subs.add("admin");
            }
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("trust")
                || args[0].equalsIgnoreCase("untrust"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        // /claim admin <player> — tab-complete with online players
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (player.hasPermission("ghostyclaim.admin") || player.hasPermission("ghostyclaim.admin.gui")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}
