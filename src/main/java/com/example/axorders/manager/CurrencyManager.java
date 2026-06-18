package com.example.axorders.manager;

import com.example.axorders.AxOrdersAddon;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;

public class CurrencyManager {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");

    private final AxOrdersAddon plugin;
    private Provider provider;
    private Object axAuctionsHook;
    private boolean loggedProvider;
    private boolean warnedAxAuctionsEmptyRegistry;

    public CurrencyManager(AxOrdersAddon plugin) {
        this.plugin = plugin;
    }

    public void init() {
        reload(false);
        scheduleRetry(20L);
        scheduleRetry(100L);
        scheduleRetry(200L);
    }

    public Object getHook() {
        if (provider == null) reload(true);
        return axAuctionsHook;
    }

    public boolean isAvailable() {
        if (provider == null) reload(true);
        return provider != null;
    }

    public void reload() {
        reload(true);
    }

    private void reload(boolean warn) {
        Provider selected = findVaultProvider();
        axAuctionsHook = null;

        if (selected == null && plugin.getConfig().getBoolean("currency.axauctions.enabled", false)) {
            selected = findAxAuctionsProvider(warn);
        }

        if (selected == null) {
            selected = new ExperienceProvider();
        }

        provider = selected;
        if (!loggedProvider && provider != null) {
            plugin.getLogger().info("Using currency provider: " + provider.name());
            loggedProvider = true;
        }
    }

    private Provider findVaultProvider() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null || registration.getProvider() == null) return null;
            return new VaultProvider(registration.getProvider());
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception ex) {
            plugin.getLogger().warning("Vault currency lookup failed: " + ex.getMessage());
            return null;
        }
    }

    private Provider findAxAuctionsProvider(boolean warn) {
        Map<?, ?> registry;
        try {
            Class<?> apiClass = Class.forName("com.artillexstudios.axauctions.api.AxAuctionsAPI");
            registry = (Map<?, ?>) apiClass.getMethod("getRegistry").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ex) {
            if (warn) {
                plugin.getLogger().warning("AxAuctions currency fallback is enabled, but AxAuctions is not available.");
            }
            return null;
        }

        String configured = plugin.getConfig().getString("currency.axauctions.currency", "Vault").trim();

        if (registry == null || registry.isEmpty()) {
            if (warn && !warnedAxAuctionsEmptyRegistry) {
                plugin.getLogger().warning("AxAuctions currency fallback is enabled, but AxAuctions has not registered any currency hooks yet.");
                warnedAxAuctionsEmptyRegistry = true;
            }
            return null;
        }

        Object found = registry.get(configured);
        if (found == null) {
            for (Map.Entry<?, ?> entry : registry.entrySet()) {
                String hookName = getAxAuctionsHookName(entry.getValue());
                if (String.valueOf(entry.getKey()).equalsIgnoreCase(configured) || hookName.equalsIgnoreCase(configured)) {
                    found = entry.getValue();
                    break;
                }
            }
        }

        if (found == null) {
            if (warn) {
                plugin.getLogger().warning("AxAuctions currency '" + configured + "' is not available. Falling back to experience. Available currencies: "
                        + registryKeys(registry));
            }
            return null;
        }

        warnedAxAuctionsEmptyRegistry = false;
        axAuctionsHook = found;
        try {
            return new AxAuctionsProvider(found);
        } catch (IllegalStateException ex) {
            if (warn) {
                plugin.getLogger().warning("AxAuctions currency hook is not compatible. Falling back to experience.");
            }
            axAuctionsHook = null;
            return null;
        }
    }

    private String getAxAuctionsHookName(Object hook) {
        if (hook == null) return "";
        try {
            return String.valueOf(hook.getClass().getMethod("getName").invoke(hook));
        } catch (ReflectiveOperationException ex) {
            return "";
        }
    }

    private String registryKeys(Map<?, ?> registry) {
        StringBuilder keys = new StringBuilder();
        for (Object key : registry.keySet()) {
            if (!keys.isEmpty()) keys.append(", ");
            keys.append(key);
        }
        return keys.toString();
    }

    private void scheduleRetry(long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled() || provider instanceof VaultProvider) return;
            reload(delayTicks >= 200L);
        }, delayTicks);
    }

    public boolean has(UUID player, double amount) {
        try {
            return isAvailable() && provider.has(player, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Currency check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean take(UUID player, double amount) {
        try {
            return isAvailable() && provider.take(player, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to take currency balance: " + e.getMessage());
            return false;
        }
    }

    public boolean give(UUID player, double amount) {
        try {
            return isAvailable() && provider.give(player, amount);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to give currency balance: " + exception.getMessage());
            return false;
        }
    }

    public String format(double amount) {
        String currency = provider == null ? "experience" : provider.name();
        return FORMAT.format(amount) + " " + currency;
    }

    private interface Provider {
        String name();

        boolean has(UUID player, double amount) throws Exception;

        boolean take(UUID player, double amount) throws Exception;

        boolean give(UUID player, double amount) throws Exception;
    }

    private class AxAuctionsProvider implements Provider {
        private final Object hook;
        private final Method getName;
        private final Method getBalance;
        private final Method takeBalance;
        private final Method giveBalance;

        private AxAuctionsProvider(Object hook) {
            this.hook = hook;
            try {
                this.getName = hook.getClass().getMethod("getName");
                this.getBalance = hook.getClass().getMethod("getBalance", UUID.class);
                this.takeBalance = hook.getClass().getMethod("takeBalance", UUID.class, double.class);
                this.giveBalance = hook.getClass().getMethod("giveBalance", UUID.class, double.class);
            } catch (NoSuchMethodException ex) {
                throw new IllegalStateException("Invalid AxAuctions currency hook", ex);
            }
        }

        @Override
        public String name() {
            try {
                return String.valueOf(getName.invoke(hook));
            } catch (Exception ex) {
                return "AxAuctions";
            }
        }

        @Override
        public boolean has(UUID player, double amount) throws Exception {
            return ((Number) getBalance.invoke(hook, player)).doubleValue() >= amount;
        }

        @Override
        public boolean take(UUID player, double amount) throws Exception {
            Object future = takeBalance.invoke(hook, player, amount);
            return (Boolean) future.getClass().getMethod("join").invoke(future);
        }

        @Override
        public boolean give(UUID player, double amount) throws Exception {
            Object future = giveBalance.invoke(hook, player, amount);
            return (Boolean) future.getClass().getMethod("join").invoke(future);
        }
    }

    private class VaultProvider implements Provider {
        private final Object economy;
        private final Method getName;
        private final Method getBalance;
        private final Method withdrawPlayer;
        private final Method depositPlayer;
        private final Method transactionSuccess;

        private VaultProvider(Object economy) throws NoSuchMethodException, ClassNotFoundException {
            this.economy = economy;
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            this.getName = economyClass.getMethod("getName");
            this.getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            this.withdrawPlayer = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            this.depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            this.transactionSuccess = responseClass.getMethod("transactionSuccess");
        }

        @Override
        public String name() {
            try {
                return String.valueOf(getName.invoke(economy));
            } catch (Exception ex) {
                return "Vault";
            }
        }

        @Override
        public boolean has(UUID player, double amount) throws Exception {
            return ((Number) getBalance.invoke(economy, Bukkit.getOfflinePlayer(player))).doubleValue() >= amount;
        }

        @Override
        public boolean take(UUID player, double amount) throws Exception {
            Object response = withdrawPlayer.invoke(economy, Bukkit.getOfflinePlayer(player), amount);
            return (Boolean) transactionSuccess.invoke(response);
        }

        @Override
        public boolean give(UUID player, double amount) throws Exception {
            Object response = depositPlayer.invoke(economy, Bukkit.getOfflinePlayer(player), amount);
            return (Boolean) transactionSuccess.invoke(response);
        }
    }

    private class ExperienceProvider implements Provider {
        @Override
        public String name() {
            return "experience";
        }

        @Override
        public boolean has(UUID player, double amount) {
            Player online = Bukkit.getPlayer(player);
            return online != null && getTotalExperience(online) >= toExperience(amount);
        }

        @Override
        public boolean take(UUID player, double amount) {
            Player online = Bukkit.getPlayer(player);
            if (online == null) return false;

            int levels = toExperience(amount);
            if (getTotalExperience(online) < levels) return false;

            setTotalExperience(online, getTotalExperience(online) - levels);
            return true;
        }

        @Override
        public boolean give(UUID player, double amount) {
            Player online = Bukkit.getPlayer(player);
            if (online == null) return false;

            online.giveExp(toExperience(amount));
            return true;
        }

        private int toExperience(double amount) {
            if (amount <= 0) return 0;
            return (int) Math.ceil(amount);
        }

        private int getTotalExperience(Player player) {
            int total = Math.round(getExperienceAtLevel(player.getLevel()) + player.getExp() * player.getExpToLevel());
            return Math.max(0, total);
        }

        private void setTotalExperience(Player player, int amount) {
            player.setExp(0);
            player.setLevel(0);
            player.setTotalExperience(0);
            player.giveExp(Math.max(0, amount));
        }

        private int getExperienceAtLevel(int level) {
            if (level <= 16) return level * level + 6 * level;
            if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }
}
