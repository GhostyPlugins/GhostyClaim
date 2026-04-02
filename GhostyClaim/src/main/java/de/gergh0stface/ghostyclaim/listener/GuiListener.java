package de.gergh0stface.ghostyclaim.listener;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.gui.*;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimFlag;
import de.gergh0stface.ghostyclaim.util.ColorUtil;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles all GUI interactions for GhostyClaim.
 */
public class GuiListener implements Listener {

    private final GhostyClaim plugin;

    public GuiListener(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!GuiRegistry.has(player)) return;

        GuiSession session = GuiRegistry.get(player);
        if (session == null) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        ItemStack clicked = event.getCurrentItem();
        int slot = event.getSlot();

        switch (session.getType()) {
            case MAIN                -> handleMain(player, session, slot, clicked);
            case FLAGS               -> handleFlags(player, session, slot, clicked);
            case TRUST               -> handleTrust(player, session, slot, clicked);
            case LIST                -> handleList(player, session, slot, clicked);
            case DELETE_CONFIRM      -> handleDeleteConfirm(player, session, slot);
            case ADMIN_LIST          -> handleAdminList(player, session, slot, clicked);
            case ADMIN_DETAIL        -> handleAdminDetail(player, session, slot, clicked);
            case ADMIN_DELETE_CONFIRM-> handleAdminDeleteConfirm(player, session, slot);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Delay removal so re-open from click doesn't flicker
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.getOpenInventory().getTitle().isEmpty()) return;
            GuiRegistry.remove(player);
        }, 1L);
    }

    // ─── MAIN GUI ─────────────────────────────────────────────

    private void handleMain(Player player, GuiSession session, int slot, ItemStack clicked) {
        UUID claimId = session.getClaimId();
        Claim claim = plugin.getClaimManager().getClaim(claimId);
        if (claim == null) { player.closeInventory(); return; }

        if (slot == ClaimMainGui.getSlotFlags()) {
            new ClaimFlagsGui(plugin).open(player, claim);

        } else if (slot == ClaimMainGui.getSlotTrust()) {
            new ClaimTrustGui(plugin).open(player, claim);

        } else if (slot == ClaimMainGui.getSlotRename()) {
            player.closeInventory();
            // Prompt rename via chat
            plugin.getRenameWaiting().put(player.getUniqueId(), claimId);
            player.sendMessage(plugin.getLangManager().get("gui.rename-item"));
            player.sendMessage(plugin.getLangManager().getPrefix()
                    + de.gergh0stface.ghostyclaim.util.ColorUtil.colorize(
                        "&7Type the new name in chat &8(or &ccancel&8):"));

        } else if (slot == ClaimMainGui.getSlotVisualize()) {
            player.closeInventory();
            plugin.getVisualizerManager().toggle(player);
            if (plugin.getVisualizerManager().isActive(player)) {
                plugin.getLangManager().send(player, "visualizer.enabled");
            } else {
                plugin.getLangManager().send(player, "visualizer.disabled");
            }

        } else if (slot == ClaimMainGui.getSlotDelete()) {
            new DeleteConfirmGui(plugin).open(player, claim);

        } else if (slot == ClaimMainGui.getSlotClose()) {
            player.closeInventory();
            GuiRegistry.remove(player);
        }
    }

    // ─── FLAGS GUI ────────────────────────────────────────────

    private void handleFlags(Player player, GuiSession session, int slot, ItemStack clicked) {
        UUID claimId = session.getClaimId();
        Claim claim = plugin.getClaimManager().getClaim(claimId);
        if (claim == null) { player.closeInventory(); return; }

        // Back button (last row, center)
        String displayName = clicked.getItemMeta() != null
                ? ColorUtil.strip(clicked.getItemMeta().getDisplayName()) : "";

        if (displayName.contains("Back") || displayName.contains("Zurück")) {
            new ClaimMainGui(plugin).open(player, claim);
            return;
        }

        // Find which flag was clicked by matching material & slot
        ClaimFlag[] flags = ClaimFlag.values();
        int contentSlot = 10;
        int col = 0;

        for (ClaimFlag flag : flags) {
            if (col == 7) { contentSlot += 3; col = 0; }
            if (contentSlot == slot) {
                // Toggle the flag
                boolean newValue = !claim.getFlag(flag);
                claim.setFlag(flag, newValue);
                plugin.getClaimManager().updateClaim(claim);

                String flagName = plugin.getLangManager().get("flags." + flagNameKey(flag));
                if (newValue) {
                    plugin.getLangManager().send(player, "flags.enabled", "flag", flagName);
                } else {
                    plugin.getLangManager().send(player, "flags.disabled", "flag", flagName);
                }
                // Refresh GUI
                new ClaimFlagsGui(plugin).open(player, claim);
                return;
            }
            contentSlot++;
            col++;
        }
    }

    // ─── TRUST GUI ────────────────────────────────────────────

    private void handleTrust(Player player, GuiSession session, int slot, ItemStack clicked) {
        UUID claimId = session.getClaimId();
        Claim claim = plugin.getClaimManager().getClaim(claimId);
        if (claim == null) { player.closeInventory(); return; }

        if (!clicked.hasItemMeta()) return;
        String displayName = ColorUtil.strip(clicked.getItemMeta().getDisplayName());

        // Back button
        if (displayName.contains("Back") || displayName.contains("Zurück")) {
            new ClaimMainGui(plugin).open(player, claim);
            return;
        }
        // Close
        if (displayName.contains("Close") || displayName.contains("Schließen")) {
            player.closeInventory(); return;
        }
        // Add trust hint — redirect to command
        if (displayName.contains("Trust a Player")) return;

        // Skull click = untrust
        if (clicked.getType() == org.bukkit.Material.PLAYER_HEAD) {
            // Find UUID from lore
            var lore = clicked.getItemMeta().getLore();
            if (lore == null) return;
            for (String line : lore) {
                String stripped = ColorUtil.strip(line);
                if (stripped.startsWith("UUID: ")) {
                    String partialUuid = stripped.replace("UUID: ", "").replace("...", "");
                    // Find matching trusted UUID
                    for (UUID trustedUUID : claim.getTrustedPlayers().keySet()) {
                        if (trustedUUID.toString().startsWith(partialUuid)) {
                            claim.removeTrusted(trustedUUID);
                            plugin.getClaimManager().updateClaim(claim);
                            plugin.getLangManager().send(player, "trust.untrusted",
                                    "player", claim.getTrustedPlayers().getOrDefault(trustedUUID, displayName));
                            new ClaimTrustGui(plugin).open(player, claim);
                            return;
                        }
                    }
                }
            }
        }
    }

    // ─── LIST GUI ─────────────────────────────────────────────

    private void handleList(Player player, GuiSession session, int slot, ItemStack clicked) {
        if (!clicked.hasItemMeta()) return;
        String displayName = ColorUtil.strip(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Next") || displayName.contains("Nächste")) {
            new ClaimListGui(plugin).open(player, session.getPage() + 1);
            return;
        }
        if (displayName.contains("Previous") || displayName.contains("Vorherige")) {
            new ClaimListGui(plugin).open(player, session.getPage() - 1);
            return;
        }
        if (displayName.contains("Close") || displayName.contains("Schließen")) {
            player.closeInventory(); return;
        }

        // Claim grass block clicked — find by name
        if (clicked.getType() == org.bukkit.Material.GRASS_BLOCK) {
            String claimName = displayName;
            for (Claim c : plugin.getClaimManager().getClaimsForPlayer(player.getUniqueId())) {
                if (ColorUtil.strip(c.getName()).equalsIgnoreCase(claimName)) {
                    new ClaimMainGui(plugin).open(player, c);
                    return;
                }
            }
        }
    }

    // ─── DELETE CONFIRM ───────────────────────────────────────

    private void handleDeleteConfirm(Player player, GuiSession session, int slot) {
        UUID claimId = session.getClaimId();
        Claim claim = plugin.getClaimManager().getClaim(claimId);
        if (claim == null) { player.closeInventory(); return; }

        if (slot == DeleteConfirmGui.SLOT_YES) {
            double refund = plugin.getEconomyManager().calculateRefund(claim.getArea());
            String claimName = claim.getName();

            plugin.getClaimManager().deleteClaim(claimId);
            player.closeInventory();
            GuiRegistry.remove(player);

            if (plugin.getEconomyManager().isEnabled() && refund > 0) {
                plugin.getEconomyManager().refund(player, refund);
                plugin.getLangManager().send(player, "economy.refund-message",
                        "amount", plugin.getEconomyManager().format(refund));
            }
            plugin.getLangManager().send(player, "claim.deleted", "name", claimName);

        } else if (slot == DeleteConfirmGui.SLOT_NO) {
            new ClaimMainGui(plugin).open(player, claim);
        }
    }

    // ─── ADMIN LIST ───────────────────────────────────────────

    private void handleAdminList(Player admin, GuiSession session, int slot, ItemStack clicked) {
        if (!clicked.hasItemMeta()) return;
        String name = ColorUtil.strip(clicked.getItemMeta().getDisplayName());

        if (name.contains("Next") || name.contains("Nächste")) {
            new AdminClaimListGui(plugin).open(admin, session.getPage() + 1, session.getFilter());
        } else if (name.contains("Previous") || name.contains("Vorherige")) {
            new AdminClaimListGui(plugin).open(admin, session.getPage() - 1, session.getFilter());
        } else if (name.contains("Close") || name.contains("Schließen")) {
            admin.closeInventory();
        } else if (clicked.getType() == org.bukkit.Material.GRASS_BLOCK) {
            // Claim selected → open detail
            for (de.gergh0stface.ghostyclaim.model.Claim c :
                    plugin.getClaimManager().getAllClaims()) {
                if (ColorUtil.strip(c.getName()).equalsIgnoreCase(name)) {
                    new AdminClaimDetailGui(plugin).open(admin, c, session.getFilter());
                    return;
                }
            }
        }
    }

    // ─── ADMIN DETAIL ─────────────────────────────────────────

    private void handleAdminDetail(Player admin, GuiSession session, int slot, ItemStack clicked) {
        UUID claimId = session.getClaimId();
        de.gergh0stface.ghostyclaim.model.Claim claim = plugin.getClaimManager().getClaim(claimId);
        if (claim == null) { admin.closeInventory(); return; }

        if (slot == AdminClaimDetailGui.SLOT_BACK) {
            new AdminClaimListGui(plugin).open(admin, 0, session.getFilter());

        } else if (slot == AdminClaimDetailGui.SLOT_CLOSE) {
            admin.closeInventory();
            GuiRegistry.remove(admin);

        } else if (slot == AdminClaimDetailGui.SLOT_TELEPORT) {
            admin.closeInventory();
            GuiRegistry.remove(admin);
            var center = claim.getCenter();
            if (center != null) {
                admin.teleport(center);
                plugin.getLangManager().send(admin, "admin-gui.teleported",
                        "name", claim.getName());
            }

        } else if (slot == AdminClaimDetailGui.SLOT_FLAGS) {
            new ClaimFlagsGui(plugin).open(admin, claim);

        } else if (slot == AdminClaimDetailGui.SLOT_RENAME) {
            admin.closeInventory();
            plugin.getRenameWaiting().put(admin.getUniqueId(), claimId);
            admin.sendMessage(plugin.getLangManager().get("gui.rename-item"));
            admin.sendMessage(plugin.getLangManager().getPrefix()
                    + de.gergh0stface.ghostyclaim.util.ColorUtil.colorize(
                        "&7Type the new name in chat &8(or &ccancel&8):"));

        } else if (slot == AdminClaimDetailGui.SLOT_CHANGE_OWNER) {
            admin.closeInventory();
            plugin.getChangeOwnerWaiting().put(admin.getUniqueId(), claimId);
            admin.sendMessage(plugin.getLangManager().get("admin-gui.change-owner-prompt",
                    "name", claim.getName()));

        } else if (slot == AdminClaimDetailGui.SLOT_TOGGLE_ALL) {
            // Toggle all flags to their opposite majority
            boolean majority = java.util.Arrays.stream(
                    de.gergh0stface.ghostyclaim.model.ClaimFlag.values())
                    .filter(claim::getFlag).count()
                    > de.gergh0stface.ghostyclaim.model.ClaimFlag.values().length / 2;
            for (var f : de.gergh0stface.ghostyclaim.model.ClaimFlag.values()) {
                claim.setFlag(f, !majority);
            }
            plugin.getClaimManager().updateClaim(claim);
            plugin.getLangManager().send(admin, "admin-gui.flags-toggled",
                    "name", claim.getName(),
                    "status", !majority ? "enabled" : "disabled");
            new AdminClaimDetailGui(plugin).open(admin, claim, session.getFilter());

        } else if (slot == AdminClaimDetailGui.SLOT_TRUSTED) {
            new ClaimTrustGui(plugin).open(admin, claim);

        } else if (slot == AdminClaimDetailGui.SLOT_DELETE) {
            // Open admin delete confirm
            openAdminDeleteConfirm(admin, claim, session.getFilter());
        }
    }

    private void openAdminDeleteConfirm(Player admin, de.gergh0stface.ghostyclaim.model.Claim claim,
                                         String filter) {
        org.bukkit.inventory.Inventory inv =
                org.bukkit.Bukkit.createInventory(null, 27,
                        plugin.getLangManager().get("admin-gui.confirm-delete-title",
                                "name", claim.getName()));

        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.filler());

        inv.setItem(11, new ItemBuilder(org.bukkit.Material.LIME_STAINED_GLASS_PANE)
                .name("&a✔ " + plugin.getLangManager().get("gui.confirm-delete-yes"))
                .lore("&7Owner: &e" + claim.getOwnerName(),
                      "&7Size: &f" + claim.getArea() + " blocks",
                      "&cNo refund for admin deletion.")
                .glow().build());

        inv.setItem(13, new ItemBuilder(org.bukkit.Material.TNT)
                .name("&f" + claim.getName())
                .lore("&7Owner: &e" + claim.getOwnerName()).build());

        inv.setItem(15, new ItemBuilder(org.bukkit.Material.RED_STAINED_GLASS_PANE)
                .name("&c✖ " + plugin.getLangManager().get("gui.confirm-delete-no"))
                .build());

        admin.openInventory(inv);
        GuiRegistry.register(admin, new GuiSession(
                GuiSession.Type.ADMIN_DELETE_CONFIRM, claim.getId(), 0, filter));
    }

    // ─── ADMIN DELETE CONFIRM ─────────────────────────────────

    private void handleAdminDeleteConfirm(Player admin, GuiSession session, int slot) {
        UUID claimId = session.getClaimId();
        de.gergh0stface.ghostyclaim.model.Claim claim = plugin.getClaimManager().getClaim(claimId);
        if (claim == null) { admin.closeInventory(); return; }

        if (slot == 11) { // yes
            String claimName  = claim.getName();
            String ownerName  = claim.getOwnerName();
            plugin.getClaimManager().deleteClaim(claimId);
            admin.closeInventory();
            GuiRegistry.remove(admin);
            plugin.getLangManager().send(admin, "admin-gui.deleted",
                    "name", claimName, "owner", ownerName);
            plugin.getLogger().info("[Admin] " + admin.getName()
                    + " deleted claim \"" + claimName + "\" owned by " + ownerName);
        } else if (slot == 15) { // no
            if (claim != null) new AdminClaimDetailGui(plugin).open(admin, claim, session.getFilter());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private String flagNameKey(ClaimFlag flag) {
        return flag.getKey().replace("_", "-") + "-name";

    }
}
