package de.gergh0stface.ghostyclaim.gui;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI for managing trusted players in a claim.
 */
public class ClaimTrustGui {

    private final GhostyClaim plugin;

    public ClaimTrustGui(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Claim claim) {
        String title = plugin.getLangManager().get("trust.gui-title");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill with filler
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.filler());

        // Place trusted player heads
        Map<UUID, String> trusted = claim.getTrustedPlayers();
        int slot = 10;
        int col = 0;

        for (Map.Entry<UUID, String> entry : trusted.entrySet()) {
            if (col >= 7) { slot += 3; col = 0; }
            if (slot >= 45) break;

            UUID trustedUUID = entry.getKey();
            String trustedName = entry.getValue();

            // Create skull item
            var skull = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&f" + trustedName)
                    .lore("&7Click to &cuntrust &7this player.",
                          "&8UUID: &7" + trustedUUID.toString().substring(0, 8) + "...")
                    .build();

            // Try to set skull texture
            try {
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(trustedUUID));
                    skull.setItemMeta(meta);
                }
            } catch (Exception ignored) {}

            inv.setItem(slot, skull);
            slot++;
            col++;
        }

        // Empty slot hint
        if (trusted.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&cNo trusted players yet!")
                    .lore("&7Use &f/claim trust <player>",
                          "&7to add someone.")
                    .build());
        }

        // Add player button (anvil input via command prompt)
        inv.setItem(49,
                new ItemBuilder(Material.LIME_DYE)
                        .name("&a+ Trust a Player")
                        .lore("&7Type &f/claim trust <player>",
                              "&7to add a new trusted player.")
                        .build());

        // Back button
        inv.setItem(45,
                new ItemBuilder(Material.ARROW)
                        .name(plugin.getLangManager().get("gui.back"))
                        .build());

        // Close
        inv.setItem(53,
                new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                        .name(plugin.getLangManager().get("gui.close"))
                        .build());

        player.openInventory(inv);
        GuiRegistry.register(player, new GuiSession(GuiSession.Type.TRUST, claim.getId()));
    }
}
