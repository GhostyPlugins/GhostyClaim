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
 * Paginated list of all claims owned by a player.
 */
public class ClaimListGui {

    public static final int PAGE_SIZE = 28; // 4 rows × 7 columns

    private final GhostyClaim plugin;

    public ClaimListGui(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<Claim> claims = plugin.getClaimManager().getClaimsForPlayer(player.getUniqueId());
        int totalPages = Math.max(1, (int) Math.ceil((double) claims.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = plugin.getLangManager().get("gui.list-title");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill border with filler
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.filler());

        if (claims.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(plugin.getLangManager().get("gui.no-claims"))
                    .build());
        } else {
            int startIdx = page * PAGE_SIZE;
            int endIdx   = Math.min(startIdx + PAGE_SIZE, claims.size());

            // Content slots: rows 1-4, columns 1-7 (skipping border)
            int slot = 10;
            int col  = 0;

            for (int i = startIdx; i < endIdx; i++) {
                if (col >= 7) { slot += 3; col = 0; }

                Claim claim = claims.get(i);
                String trustedStr = claim.getTrustedPlayers().isEmpty()
                        ? "None"
                        : String.join(", ", claim.getTrustedPlayers().values());

                inv.setItem(slot, new ItemBuilder(Material.GRASS_BLOCK)
                        .name("&a" + claim.getName())
                        .lore(
                            "&7World: &f" + claim.getWorldName(),
                            "&7Size: &f" + claim.getArea() + " blocks",
                            "&7Coords: &f" + claim.getMinX() + "," + claim.getMinZ()
                                    + " &7→ &f" + claim.getMaxX() + "," + claim.getMaxZ(),
                            "&7Trusted: &f" + trustedStr,
                            "",
                            "&eClick to manage!"
                        )
                        .build());

                slot++;
                col++;
            }
        }

        // Prev page
        if (page > 0) {
            inv.setItem(45, new ItemBuilder(Material.ARROW)
                    .name(plugin.getLangManager().get("gui.prev-page"))
                    .build());
        }

        // Next page
        if (page < totalPages - 1) {
            inv.setItem(53, new ItemBuilder(Material.ARROW)
                    .name(plugin.getLangManager().get("gui.next-page"))
                    .build());
        }

        // Page indicator
        inv.setItem(49, new ItemBuilder(Material.PAPER)
                .name("&7Page &f" + (page + 1) + "&7/&f" + totalPages)
                .build());

        // Close
        inv.setItem(48, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(plugin.getLangManager().get("gui.close"))
                .build());

        player.openInventory(inv);
        GuiRegistry.register(player, new GuiSession(GuiSession.Type.LIST, null, page));
    }
}
