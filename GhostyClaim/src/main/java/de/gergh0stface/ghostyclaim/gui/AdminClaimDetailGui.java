package de.gergh0stface.ghostyclaim.gui;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimFlag;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Admin GUI — detail view for a single claim with delete / edit / teleport / change-owner actions.
 */
public class AdminClaimDetailGui {

    // ── Fixed slot layout ────────────────────────────────────
    public static final int SLOT_INFO         = 4;
    public static final int SLOT_TELEPORT     = 10;
    public static final int SLOT_FLAGS        = 12;
    public static final int SLOT_RENAME       = 14;
    public static final int SLOT_CHANGE_OWNER = 16;
    public static final int SLOT_TOGGLE_ALL   = 20;
    public static final int SLOT_TRUSTED      = 22;
    public static final int SLOT_DELETE       = 24;
    public static final int SLOT_BACK         = 36;
    public static final int SLOT_CLOSE        = 44;

    private final GhostyClaim plugin;

    public AdminClaimDetailGui(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player admin, Claim claim, String previousFilter) {
        String title = plugin.getLangManager().get("admin-gui.detail-title",
                "name", claim.getName());
        Inventory inv = Bukkit.createInventory(null, 45, title);

        for (int i = 0; i < 45; i++) inv.setItem(i, ItemBuilder.filler());

        String trustedStr = claim.getTrustedPlayers().isEmpty()
                ? "None"
                : String.join(", ", claim.getTrustedPlayers().values());

        // ── Info ──────────────────────────────────────────────
        inv.setItem(SLOT_INFO, new ItemBuilder(Material.BOOK)
                .name("&f&l" + claim.getName())
                .lore(
                    "&7Owner:   &e" + claim.getOwnerName(),
                    "&7UUID:    &8" + claim.getOwnerUUID().toString().substring(0, 12) + "...",
                    "&7World:   &f" + claim.getWorldName(),
                    "&7Size:    &f" + claim.getArea() + " blocks",
                    "&7Corners: &f" + claim.getMinX() + "," + claim.getMinZ()
                            + " &8→ &f" + claim.getMaxX() + "," + claim.getMaxZ(),
                    "&7Trusted: &f" + trustedStr
                )
                .build());

        // ── Teleport ──────────────────────────────────────────
        inv.setItem(SLOT_TELEPORT, new ItemBuilder(Material.ENDER_PEARL)
                .name(plugin.getLangManager().get("admin-gui.teleport-item"))
                .lore(plugin.getLangManager().getList("admin-gui.teleport-item-lore",
                        "name", claim.getName()))
                .build());

        // ── Flags ─────────────────────────────────────────────
        inv.setItem(SLOT_FLAGS, new ItemBuilder(Material.ORANGE_BANNER)
                .name(plugin.getLangManager().get("gui.flags-item"))
                .lore(plugin.getLangManager().getList("gui.flags-item-lore"))
                .glow()
                .build());

        // ── Rename ────────────────────────────────────────────
        inv.setItem(SLOT_RENAME, new ItemBuilder(Material.NAME_TAG)
                .name(plugin.getLangManager().get("gui.rename-item"))
                .lore(plugin.getLangManager().getList("gui.rename-item-lore"))
                .build());

        // ── Change Owner ──────────────────────────────────────
        inv.setItem(SLOT_CHANGE_OWNER, new ItemBuilder(Material.PLAYER_HEAD)
                .name(plugin.getLangManager().get("admin-gui.change-owner-item"))
                .lore(plugin.getLangManager().getList("admin-gui.change-owner-item-lore",
                        "owner", claim.getOwnerName()))
                .build());

        // ── Toggle All Flags ──────────────────────────────────
        boolean allEnabled = ClaimFlag.values().length > 0
                && java.util.Arrays.stream(ClaimFlag.values()).allMatch(claim::getFlag);
        inv.setItem(SLOT_TOGGLE_ALL, new ItemBuilder(Material.LEVER)
                .name(plugin.getLangManager().get("admin-gui.toggle-all-item"))
                .lore(plugin.getLangManager().getList("admin-gui.toggle-all-item-lore",
                        "status", allEnabled
                                ? plugin.getLangManager().get("flags.status-on")
                                : plugin.getLangManager().get("flags.status-off")))
                .build());

        // ── Manage Trusted ────────────────────────────────────
        inv.setItem(SLOT_TRUSTED, new ItemBuilder(Material.PLAYER_HEAD)
                .name(plugin.getLangManager().get("gui.trust-item"))
                .lore(plugin.getLangManager().getList("gui.trust-item-lore"))
                .build());

        // ── Delete ────────────────────────────────────────────
        inv.setItem(SLOT_DELETE, new ItemBuilder(Material.TNT)
                .name(plugin.getLangManager().get("admin-gui.delete-item"))
                .lore(plugin.getLangManager().getList("admin-gui.delete-item-lore",
                        "name", claim.getName(),
                        "owner", claim.getOwnerName()))
                .glow()
                .build());

        // ── Back / Close ──────────────────────────────────────
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(plugin.getLangManager().get("gui.back")).build());

        inv.setItem(SLOT_CLOSE, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(plugin.getLangManager().get("gui.close")).build());

        admin.openInventory(inv);
        GuiRegistry.register(admin, new GuiSession(
                GuiSession.Type.ADMIN_DETAIL, claim.getId(), 0, previousFilter));
    }
}
