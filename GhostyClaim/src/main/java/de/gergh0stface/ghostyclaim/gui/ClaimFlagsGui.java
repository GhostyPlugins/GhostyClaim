package de.gergh0stface.ghostyclaim.gui;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimFlag;
import de.gergh0stface.ghostyclaim.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;

/**
 * GUI for managing claim flags.
 */
public class ClaimFlagsGui {

    private final GhostyClaim plugin;

    public ClaimFlagsGui(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Claim claim) {
        ClaimFlag[] flags = ClaimFlag.values();
        // Round up to nearest multiple of 9, plus 9 for bottom row
        int rows = Math.max(3, (int) Math.ceil((flags.length + 9) / 9.0) + 1);
        rows = Math.min(rows, 6);

        String title = plugin.getLangManager().get("flags.gui-title");
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        // Fill with filler
        for (int i = 0; i < rows * 9; i++) inv.setItem(i, ItemBuilder.filler());

        // Place each flag item
        int slot = 10;
        int col = 0;
        for (ClaimFlag flag : flags) {
            if (col == 7) { slot += 3; col = 0; } // skip border columns

            boolean enabled = claim.getFlag(flag);

            String name = plugin.getLangManager().get("flags." + flagNameKey(flag));
            String desc  = plugin.getLangManager().get("flags." + flagDescKey(flag));
            String status = enabled
                    ? plugin.getLangManager().get("flags.status-on")
                    : plugin.getLangManager().get("flags.status-off");
            String toggle = plugin.getLangManager().get("flags.click-to-toggle");

            List<String> lore = Arrays.asList(
                    "&7" + desc,
                    "",
                    status,
                    toggle
            );

            Material mat = flag.getGuiMaterial();
            ItemBuilder builder = new ItemBuilder(mat)
                    .name(name)
                    .lore(lore);
            if (enabled) builder.glow();

            inv.setItem(slot, builder.build());
            slot++;
            col++;

            if (slot >= (rows - 1) * 9) break; // don't overflow last row
        }

        // Back button
        int backSlot = (rows - 1) * 9 + 4;
        inv.setItem(backSlot,
                new ItemBuilder(Material.ARROW)
                        .name(plugin.getLangManager().get("gui.back"))
                        .build());

        player.openInventory(inv);
        GuiRegistry.register(player, new GuiSession(GuiSession.Type.FLAGS, claim.getId()));
    }

    private String flagNameKey(ClaimFlag flag) {
        return flag.getKey().replace("_", "-") + "-name";
    }

    private String flagDescKey(ClaimFlag flag) {
        return flag.getKey().replace("_", "-") + "-desc";
    }
}
