package de.gergh0stface.ghostyclaim.gui;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin GUI — paginated list of every claim on the server.
 * Supports filtering by player name.
 */
public class AdminClaimListGui {

    public static final int PAGE_SIZE = 28;

    private final GhostyClaim plugin;

    public AdminClaimListGui(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player admin, int page, String filterOwner) {
        List<Claim> all = new ArrayList<>(plugin.getClaimManager().getAllClaims());

        // Apply optional owner filter
        if (filterOwner != null && !filterOwner.isBlank()) {
            String lf = filterOwner.toLowerCase();
            all = all.stream()
                    .filter(c -> c.getOwnerName().toLowerCase().contains(lf))
                    .collect(Collectors.toList());
        }

        // Sort: owner name, then claim name
        all.sort(Comparator.comparing(Claim::getOwnerName)
                           .thenComparing(Claim::getName));

        int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = plugin.getLangManager().get("admin-gui.list-title");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill border
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.filler());

        if (all.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(plugin.getLangManager().get("admin-gui.no-claims"))
                    .build());
        } else {
            int startIdx = page * PAGE_SIZE;
            int endIdx   = Math.min(startIdx + PAGE_SIZE, all.size());

            int slot = 10, col = 0;
            for (int i = startIdx; i < endIdx; i++) {
                if (col >= 7) { slot += 3; col = 0; }
                Claim c = all.get(i);

                inv.setItem(slot, new ItemBuilder(Material.GRASS_BLOCK)
                        .name("&f" + c.getName())
                        .lore(
                            "&7Owner: &e" + c.getOwnerName(),
                            "&7World: &f" + c.getWorldName(),
                            "&7Size:  &f" + c.getArea() + " blocks",
                            "&7Coords: &f" + c.getMinX() + "," + c.getMinZ()
                                    + " &7→ &f" + c.getMaxX() + "," + c.getMaxZ(),
                            "",
                            "&eClick to manage!"
                        )
                        .build());
                slot++; col++;
            }
        }

        // Filter indicator
        String filterLabel = filterOwner == null || filterOwner.isBlank()
                ? "&7All owners"
                : "&e" + filterOwner;
        inv.setItem(45, new ItemBuilder(Material.NAME_TAG)
                .name(plugin.getLangManager().get("admin-gui.filter-item"))
                .lore("&7Current filter: " + filterLabel,
                      "&7Use &f/claim admin <player> &7to filter.")
                .build());

        // Stats
        inv.setItem(46, new ItemBuilder(Material.COMPASS)
                .name(plugin.getLangManager().get("admin-gui.stats-item"))
                .lore("&7Total claims: &f" + plugin.getClaimManager().getAllClaims().size(),
                      "&7Showing: &f" + all.size(),
                      "&7Page: &f" + (page + 1) + "&7/&f" + totalPages)
                .build());

        // Prev page
        if (page > 0) {
            inv.setItem(47, new ItemBuilder(Material.ARROW)
                    .name(plugin.getLangManager().get("gui.prev-page")).build());
        }

        // Page indicator
        inv.setItem(49, new ItemBuilder(Material.PAPER)
                .name("&7Page &f" + (page + 1) + "&7/&f" + totalPages).build());

        // Next page
        if (page < totalPages - 1) {
            inv.setItem(51, new ItemBuilder(Material.ARROW)
                    .name(plugin.getLangManager().get("gui.next-page")).build());
        }

        // Close
        inv.setItem(53, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(plugin.getLangManager().get("gui.close")).build());

        admin.openInventory(inv);
        GuiRegistry.register(admin, new GuiSession(
                GuiSession.Type.ADMIN_LIST, null, page, filterOwner));
    }
}
