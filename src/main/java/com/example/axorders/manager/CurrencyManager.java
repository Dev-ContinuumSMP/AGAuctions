package com.example.axorders.manager;

import com.artillexstudios.axauctions.api.AxAuctionsAPI;
import com.artillexstudios.axauctions.hooks.currency.CurrencyHook;
import com.example.axorders.AxOrdersAddon;
import org.bukkit.Bukkit;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;

public class CurrencyManager {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");
    
    private final AxOrdersAddon plugin;
    private CurrencyHook hook;
    private boolean warnedEmptyRegistry;
    private boolean loggedHook;

    public CurrencyManager(AxOrdersAddon plugin) {
        this.plugin = plugin;
    }

    public void init() {
        reload(false);
        scheduleRetry(20L);
        scheduleRetry(100L);
        scheduleRetry(200L);
    }

    public CurrencyHook getHook() {
        if (hook == null) {
            reload(true);
        }
        return hook;
    }

    public void reload() {
        reload(true);
    }

    private void reload(boolean warn) {
        Map<String, CurrencyHook> registry = AxAuctionsAPI.getRegistry();
        String configured = plugin.getConfig().getString("currency", "Vault").trim();

        if (registry == null || registry.isEmpty()) {
            if (warn && !warnedEmptyRegistry) {
                plugin.getLogger().warning("AxAuctions has not registered any currency hooks yet. "
                        + "If you use Vault, make sure both Vault and an economy plugin are installed, then restart.");
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
    
        String fallbackKey = null;
        if (found == null && !registry.isEmpty()) {
            Map.Entry<String, CurrencyHook> fallback = registry.entrySet().iterator().next();
            fallbackKey = fallback.getKey();
            found = fallback.getValue();
        }
    
        this.hook = found;
        warnedEmptyRegistry = false;

        if (hook == null) {
            if (warn) {
                plugin.getLogger().warning("Could not find AxAuctions currency '" + configured
                        + "'. Available currencies: " + String.join(", ", registry.keySet()));
            }
        } else if (fallbackKey != null && warn) {
            plugin.getLogger().warning("AxAuctions currency '" + configured + "' is not available. "
                    + "Defaulting to '" + hook.getName() + "' (" + fallbackKey + "). Available currencies: "
                    + String.join(", ", registry.keySet()));
        } else if (!loggedHook) {
            plugin.getLogger().info("Using AxAuctions currency hook: " + hook.getName());
            loggedHook = true;
        }
    }

    private void scheduleRetry(long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled() || hook != null) return;
            reload(delayTicks >= 200L);
        }, delayTicks);
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
