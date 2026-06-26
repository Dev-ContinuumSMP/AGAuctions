package com.example.buyorders.manager;

import com.example.buyorders.BuyOrders;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.UUID;

public class CurrencyManager {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");

    private final BuyOrders plugin;
    private Provider provider;
    private boolean loggedProvider;

    public CurrencyManager(BuyOrders plugin) {
        this.plugin = plugin;
    }

    public void init() {
        reload();
    }

    public boolean isAvailable() {
        if (provider == null) reload();
        return provider != null;
    }

    public void reload() {
        Provider selected = findVaultProvider();

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

    public boolean has(UUID player, double amount) {
        try {
            return isAvailable() && provider != null && provider.has(player, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Currency check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean take(UUID player, double amount) {
        try {
            return isAvailable() && provider != null && provider.take(player, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to take currency balance: " + e.getMessage());
            return false;
        }
    }

    public boolean give(UUID player, double amount) {
        try {
            return isAvailable() && provider != null && provider.give(player, amount);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to give currency balance: " + exception.getMessage());
            return false;
        }
    }

    public String format(double amount) {
        String currency = provider == null ? "experience" : provider.name();
        String template = plugin.getConfig().getString("currency.display-format", "{amount} {currency}");
        return template
                .replace("{amount}", FORMAT.format(amount))
                .replace("{currency}", currency)
                .replace("{currency_name}", currency);
    }

    public String formatPlain(double amount) {
        return FORMAT.format(amount);
    }

    private interface Provider {
        String name();

        boolean has(UUID player, double amount) throws Exception;

        boolean take(UUID player, double amount) throws Exception;

        boolean give(UUID player, double amount) throws Exception;
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
