package de.gergh0stface.ghostyclaim.gui;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Confirmation dialog before deleting a claim.
 */
public class DeleteConfirmGui {

    public static final int SLOT_YES = 11;
    public static final int SLOT_NO  = 15;

    private final GhostyClaim plugin;

    public DeleteConfirmGui(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Claim claim) {
        String title = plugin.getLangManager().get("gui.confirm-delete-title");
        Inventory inv = Bukkit.createInventory(null, 27, title);

        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.filler());

        double refund = plugin.getEconomyManager().calculateRefund(claim.getArea());
        String refundStr = plugin.getEconomyManager().isEnabled()
                ? plugin.getEconomyManager().format(refund) : "—";

        // Yes
        inv.setItem(SLOT_YES, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(plugin.getLangManager().get("gui.confirm-delete-yes"))
                .lore("&7Refund: &a" + refundStr)
                .glow()
                .build());

        // Claim info
        inv.setItem(13, new ItemBuilder(Material.GRASS_BLOCK)
                .name("&f" + claim.getName())
                .lore("&7Size: &f" + claim.getArea() + " blocks",
                      "&7World: &f" + claim.getWorldName())
                .build());

        // No
        inv.setItem(SLOT_NO, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(plugin.getLangManager().get("gui.confirm-delete-no"))
                .build());

        player.openInventory(inv);
        GuiRegistry.register(player, new GuiSession(GuiSession.Type.DELETE_CONFIRM, claim.getId()));
    }
}
