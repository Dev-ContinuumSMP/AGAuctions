package com.example.axorders.manager;

import com.artillexstudios.axauctions.api.AxAuctionsAPI;
import com.artillexstudios.axauctions.hooks.currency.CurrencyHook;
import com.example.axorders.AxOrdersAddon;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;

public class CurrencyManager {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");
    
    private final AxOrdersAddon plugin;
    private CurrencyHook hook;
    private boolean warnedEmptyRegistry;

    public CurrencyManager(AxOrdersAddon plugin) {
        this.plugin = plugin;
    }

    public void init() {
        reload();
    }

    public CurrencyHook getHook() {
        if (hook == null) {
            reload();
        }
        return hook;
    }

    public void reload() {
        Map<String, CurrencyHook> registry = AxAuctionsAPI.getRegistry();
        String configured = plugin.getConfig().getString("currency", "Vault").trim();

        if (registry == null || registry.isEmpty()) {
            if (!warnedEmptyRegistry) {
                plugin.getLogger().warning("AxAuctions currency registry is empty. Will retry when currency is needed.");
                warnedEmptyRegistry = true;
            }
            this.hook = null;
            return;
        }
    
        CurrencyHook found = registry.get(configured);
    
        if (found == null) {
            for (Map.Entry<String, CurrencyHook> entry : registry.entrySet()) {
                String hookName = entry.getValue() == null ? "" : entry.getValue().getName();
                if (entry.getKey().equalsIgnoreCase(configured)
                        || hookName.equalsIgnoreCase(configured)) {
                    found = entry.getValue();
                    break;
                }
            }
        }
    
        if (found == null && !registry.isEmpty()) {
            found = registry.values().iterator().next();
        }
    
        this.hook = found;
        warnedEmptyRegistry = false;

        if (hook == null) {
            plugin.getLogger().warning("Could not find AxAuctions currency '" + configured
                    + "'. Available currencies: " + String.join(", ", registry.keySet()));
        } else {
            plugin.getLogger().info("Using AxAuctions currency hook: " + hook.getName());
        }
    }

    public boolean has(UUID player, double amount) {
        try {
            CurrencyHook current = getHook();
            return current != null && current.getBalance(player) >= amount;
        } catch (Exception e) {
            plugin.getLogger().warning("Currency check failed: " + e.getMessage());
            return false;
        }
    }
    public boolean take(UUID player, double amount) {
        try {
            CurrencyHook current = getHook();
            return current != null && current.takeBalance(player, amount).join();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to take AxAuctions currency balance: " + e.getMessage());
            return false;
        }
    }

    public boolean give(UUID player, double amount) {
        try {
            CurrencyHook current = getHook();
            return current != null && current.giveBalance(player, amount).join();
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to give AxAuctions currency balance: " + exception.getMessage());
            return false;
        }
    }

    public String format(double amount) {
        String currency = hook == null
                ? plugin.getConfig().getString("currency", "money")
                : hook.getName();

        return FORMAT.format(amount) + " " + currency;
    }
}
