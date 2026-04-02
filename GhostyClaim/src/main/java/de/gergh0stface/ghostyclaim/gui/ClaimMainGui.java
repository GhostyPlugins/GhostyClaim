package de.gergh0stface.ghostyclaim.gui;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Main claim management GUI (flags, trust, rename, delete, visualize, info).
 */
public class ClaimMainGui {

    public static final String TITLE_KEY = "gui.main-title";

    private static final int SLOT_INFO      = 10;
    private static final int SLOT_FLAGS     = 12;
    private static final int SLOT_TRUST     = 14;
    private static final int SLOT_RENAME    = 20;
    private static final int SLOT_VISUALIZE = 22;
    private static final int SLOT_DELETE    = 24;
    private static final int SLOT_CLOSE     = 40;

    private final GhostyClaim plugin;

    public ClaimMainGui(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Claim claim) {
        String title = plugin.getLangManager().get(TITLE_KEY);
        Inventory inv = Bukkit.createInventory(null, 45, title);

        // Fill border
        ItemBuilder.filler();
        for (int i = 0; i < 45; i++) inv.setItem(i, ItemBuilder.filler());

        // ─── Info item ────────────────────────────────────────
        String trustedStr = claim.getTrustedPlayers().isEmpty()
                ? plugin.getLangManager().get("gui.no-claims")
                : String.join(", ", claim.getTrustedPlayers().values());

        List<String> infoLore = plugin.getLangManager().getList("gui.info-item-lore",
                "owner", claim.getOwnerName(),
                "area",  claim.getArea(),
                "trusted", trustedStr);

        inv.setItem(SLOT_INFO,
                new ItemBuilder(Material.COMPASS)
                        .name(plugin.getLangManager().get("gui.info-item"))
                        .lore(infoLore)
                        .build());

        // ─── Flags item ───────────────────────────────────────
        inv.setItem(SLOT_FLAGS,
                new ItemBuilder(Material.ORANGE_BANNER)
                        .name(plugin.getLangManager().get("gui.flags-item"))
                        .lore(plugin.getLangManager().getList("gui.flags-item-lore"))
                        .glow()
                        .build());

        // ─── Trust item ───────────────────────────────────────
        inv.setItem(SLOT_TRUST,
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name(plugin.getLangManager().get("gui.trust-item"))
                        .lore(plugin.getLangManager().getList("gui.trust-item-lore"))
                        .build());

        // ─── Rename item ──────────────────────────────────────
        inv.setItem(SLOT_RENAME,
                new ItemBuilder(Material.NAME_TAG)
                        .name(plugin.getLangManager().get("gui.rename-item"))
                        .lore(plugin.getLangManager().getList("gui.rename-item-lore"))
                        .build());

        // ─── Visualize item ───────────────────────────────────
        boolean visualActive = plugin.getVisualizerManager().isActive(player);
        ItemBuilder visBuilder = new ItemBuilder(Material.PRISMARINE_CRYSTALS)
                .name(plugin.getLangManager().get("gui.visualize-item"))
                .lore(plugin.getLangManager().getList("gui.visualize-item-lore"));
        if (visualActive) visBuilder.glow();
        inv.setItem(SLOT_VISUALIZE, visBuilder.build());

        // ─── Delete item ──────────────────────────────────────
        double refund = plugin.getEconomyManager().calculateRefund(claim.getArea());
        String refundStr = plugin.getEconomyManager().isEnabled()
                ? plugin.getEconomyManager().format(refund) : "—";

        inv.setItem(SLOT_DELETE,
                new ItemBuilder(Material.BARRIER)
                        .name(plugin.getLangManager().get("gui.delete-item"))
                        .lore(plugin.getLangManager().getList("gui.delete-item-lore", "refund", refundStr))
                        .build());

        // ─── Close item ───────────────────────────────────────
        inv.setItem(SLOT_CLOSE,
                new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                        .name(plugin.getLangManager().get("gui.close"))
                        .build());

        player.openInventory(inv);
        GuiRegistry.register(player, new GuiSession(GuiSession.Type.MAIN, claim.getId()));
    }

    public static int getSlotFlags()     { return SLOT_FLAGS;     }
    public static int getSlotTrust()     { return SLOT_TRUST;     }
    public static int getSlotRename()    { return SLOT_RENAME;    }
    public static int getSlotVisualize() { return SLOT_VISUALIZE; }
    public static int getSlotDelete()    { return SLOT_DELETE;    }
    public static int getSlotClose()     { return SLOT_CLOSE;     }
}
