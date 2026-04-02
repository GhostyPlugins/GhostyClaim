package de.gergh0stface.ghostyclaim.command;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /claimreload — reloads config, lang files, and refreshes the claim cache.
 */
public class ClaimReloadCommand implements CommandExecutor {

    private final GhostyClaim plugin;

    public ClaimReloadCommand(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ghostyclaim.admin.reload")) {
            if (sender instanceof Player player) {
                plugin.getLangManager().send(player, "general.no-permission");
            } else {
                sender.sendMessage("You don't have permission.");
            }
            return true;
        }

        plugin.reload();

        if (sender instanceof Player player) {
            plugin.getLangManager().send(player, "general.reload-success");
            // Show rank-permissions summary so admin can verify their config
            sendRankSummary(player);
        } else {
            sender.sendMessage("[GhostyClaim] Plugin reloaded successfully.");
            sendRankSummaryConsole(sender);
        }
        return true;
    }

    private void sendRankSummary(Player player) {
        var lp = plugin.getLuckPermsManager();
        String prefix = plugin.getLangManager().getPrefix();
        player.sendMessage(prefix + "&8&m                              ");
        player.sendMessage(prefix + "&7LuckPerms: "
                + (lp.isAvailable() ? "&aconnected" : "&cnot found"));
        player.sendMessage(prefix + "&7Meta-Key: &f" + lp.getMetaKey());
        player.sendMessage(prefix + "&7Permission-Prefix: &f"
                + plugin.getConfig().getString("rank-permissions.permission-prefix",
                        "ghostyclaim.maxclaims."));
        var nodes = lp.getPermissionNodes();
        player.sendMessage(prefix + "&7Loaded nodes &8(" + nodes.size() + "&8): &f" + nodes);
        player.sendMessage(prefix + "&7Default max-claims: &f"
                + plugin.getConfig().getInt("claims.max-per-player", 5));
        player.sendMessage(prefix + "&8&m                              ");
    }

    private void sendRankSummaryConsole(CommandSender sender) {
        var lp = plugin.getLuckPermsManager();
        sender.sendMessage("[GhostyClaim] LuckPerms: "
                + (lp.isAvailable() ? "connected" : "not found"));
        sender.sendMessage("[GhostyClaim] Meta-key: " + lp.getMetaKey());
        sender.sendMessage("[GhostyClaim] Permission-nodes: " + lp.getPermissionNodes());
        sender.sendMessage("[GhostyClaim] Default max-claims: "
                + plugin.getConfig().getInt("claims.max-per-player", 5));
    }
}
