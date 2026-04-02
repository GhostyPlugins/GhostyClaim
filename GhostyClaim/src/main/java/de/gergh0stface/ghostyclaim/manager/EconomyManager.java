package de.gergh0stface.ghostyclaim.manager;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Handles Vault economy integration.
 */
public class EconomyManager {

    private final GhostyClaim plugin;
    private Economy economy;
    private boolean enabled;

    public EconomyManager(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempt to hook into Vault's economy provider.
     */
    public void setup() {
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            enabled = false;
            plugin.getLogger().info("Economy disabled in config.");
            return;
        }

        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            enabled = false;
            plugin.getLogger().warning("Vault not found — economy features disabled.");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            enabled = false;
            plugin.getLogger().warning("No economy provider found — economy features disabled.");
            return;
        }

        economy = rsp.getProvider();
        enabled = true;
        plugin.getLogger().info("Economy hooked: " + economy.getName());
    }

    /**
     * Whether economy is available.
     */
    public boolean isEnabled() {
        return enabled && economy != null;
    }

    /**
     * Get a player's current balance.
     */
    public double getBalance(Player player) {
        if (!isEnabled()) return Double.MAX_VALUE;
        return economy.getBalance(player);
    }

    /**
     * Check if a player can afford the given amount.
     */
    public boolean canAfford(Player player, double amount) {
        if (!isEnabled()) return true;
        return economy.has(player, amount);
    }

    /**
     * Charge a player the given amount.
     * @return true if successful
     */
    public boolean charge(Player player, double amount) {
        if (!isEnabled()) return true;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Give a player the given amount.
     * @return true if successful
     */
    public boolean refund(Player player, double amount) {
        if (!isEnabled()) return true;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Format an amount using the economy's formatting.
     */
    public String format(double amount) {
        if (!isEnabled()) return String.valueOf(amount);
        return economy.format(amount);
    }

    /**
     * Calculate the cost of creating a claim with the given area.
     */
    public double calculateClaimCost(int area) {
        double flat = plugin.getConfig().getDouble("economy.claim-flat-cost", 100.0);
        double perBlock = plugin.getConfig().getDouble("economy.claim-cost-per-block", 0.5);
        return flat + (area * perBlock);
    }

    /**
     * Calculate the refund for deleting a claim with the given area.
     */
    public double calculateRefund(int area) {
        double total = calculateClaimCost(area);
        double percent = plugin.getConfig().getDouble("economy.delete-refund-percent", 50.0) / 100.0;
        return Math.round(total * percent * 100.0) / 100.0;
    }
}
