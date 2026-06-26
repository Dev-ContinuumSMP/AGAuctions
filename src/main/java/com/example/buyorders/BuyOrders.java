package com.example.buyorders;

import com.example.buyorders.command.BuyOrderCommand;
import com.example.buyorders.command.AuctionHouseCommand;
import com.example.buyorders.command.OrdersCommand;
import com.example.buyorders.manager.AuctionHouseManager;
import com.example.buyorders.listener.OrdersGuiListener;
import com.example.buyorders.listener.PlayerJoinListener;
import com.example.buyorders.manager.CurrencyManager;
import com.example.buyorders.manager.OrderManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;
import java.util.Map;

public class BuyOrders extends JavaPlugin {

    private OrderManager orderManager;
    private AuctionHouseManager auctionHouseManager;
    private CurrencyManager currencyManager;
    private FileConfiguration localeConfig;
    private FileConfiguration webhookConfig;
    private FileConfiguration itemGroupConfig;
    private FileConfiguration blacklistedItemsConfig;
    private FileConfiguration guiFeatureMatrixConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAllPluginConfigs();
        currencyManager = new CurrencyManager(this);
        currencyManager.init();

        if (!currencyManager.isAvailable()) {
            getLogger().warning("No supported currency provider detected yet. Falling back to experience currency.");
        }
        
        orderManager = new OrderManager(this);
        orderManager.load();

        auctionHouseManager = new AuctionHouseManager(this);
        auctionHouseManager.load();

        //Listeners
        register(new OrdersGuiListener(this));
        register(new PlayerJoinListener(this));

        //Commands
        OrdersCommand ordersCommand = new OrdersCommand(this);
        BuyOrderCommand buyOrderCommand = new BuyOrderCommand(this);
        AuctionHouseCommand auctionHouseCommand = new AuctionHouseCommand(this);
        
        if (getCommand("orders") != null) {
            var cmd = getCommand("orders");
            cmd.setExecutor(ordersCommand);
            cmd.setTabCompleter(ordersCommand);
        }
        
        if (getCommand("buyorder") != null) {
            var cmd = getCommand("buyorder");
            cmd.setExecutor(buyOrderCommand);
            cmd.setTabCompleter(buyOrderCommand);
        }

        if (getCommand("ah") != null) {
            var cmd = getCommand("ah");
            cmd.setExecutor(auctionHouseCommand);
            cmd.setTabCompleter(auctionHouseCommand);
        }
        
        getLogger().info("AGAuctions enabled.");
    }

    public void reloadAllPluginConfigs() {
        reloadConfig();
        loadGuiConfigOverrides();
        loadExtraConfigs();
    }

    private void loadGuiConfigOverrides() {
        saveResource("guis.yml", false);
        File guiFile = new File(getDataFolder(), "guis.yml");
        if (!guiFile.exists()) return;

        FileConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        applySectionOverride(guiConfig, "gui");
        applySectionOverride(guiConfig, "fill-gui");
        applySectionOverride(guiConfig, "collection-gui");
        applySectionOverride(guiConfig, "ah-gui");
    }

    private void loadExtraConfigs() {
        localeConfig = loadYamlConfig("locale.yml");
        webhookConfig = loadYamlConfig("webhook-dispatch.yml");
        itemGroupConfig = loadYamlConfig("item-groups.yml");
        blacklistedItemsConfig = loadYamlConfig("blacklisted-items.yml");
        guiFeatureMatrixConfig = loadYamlConfig("gui-feature-matrix.yml");
    }

    private FileConfiguration loadYamlConfig(String fileName) {
        saveResource(fileName, false);
        File file = new File(getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void applySectionOverride(FileConfiguration source, String rootPath) {
        ConfigurationSection section = source.getConfigurationSection(rootPath);
        if (section == null) return;

        getConfig().set(rootPath, null);
        for (Map.Entry<String, Object> entry : section.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) continue;
            getConfig().set(rootPath + "." + entry.getKey(), entry.getValue());
        }
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        if (orderManager != null) orderManager.save();
        if (auctionHouseManager != null) auctionHouseManager.save();
        if (auctionHouseManager != null) auctionHouseManager.close();
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public FileConfiguration getLocaleConfig() {
        return localeConfig;
    }

    public FileConfiguration getWebhookConfig() {
        return webhookConfig;
    }

    public FileConfiguration getItemGroupConfig() {
        return itemGroupConfig;
    }

    public FileConfiguration getBlacklistedItemsConfig() {
        return blacklistedItemsConfig;
    }

    public boolean isItemBlacklisted(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        FileConfiguration cfg = blacklistedItemsConfig;
        if (cfg == null || !cfg.getBoolean("enabled", true)) return false;

        Material type = item.getType();
        String materialName = type.name();

        for (String pattern : cfg.getStringList("materials")) {
            if (wildcardMatches(materialName, pattern)) {
                return true;
            }
        }

        if (!cfg.getBoolean("match-display-name", false)) {
            return false;
        }

        var meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;

        String displayName = ChatColor.stripColor(meta.getDisplayName());
        if (displayName == null || displayName.isBlank()) return false;

        for (String pattern : cfg.getStringList("names")) {
            if (wildcardMatches(displayName, pattern)) {
                return true;
            }
        }

        return false;
    }

    private boolean wildcardMatches(String value, String pattern) {
        if (value == null || pattern == null) return false;

        String v = value.trim().toLowerCase(Locale.ROOT);
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return false;
        if (p.equals("*")) return true;

        if (!p.contains("*")) {
            return v.equals(p);
        }

        String regex = p.replace(".", "\\.").replace("*", ".*");
        return v.matches(regex);
    }

    public FileConfiguration getGuiFeatureMatrixConfig() {
        return guiFeatureMatrixConfig;
    }

    public boolean isGuiFeatureEnabled(String guiKey, String featureKey) {
        FileConfiguration matrix = guiFeatureMatrixConfig;
        if (matrix == null) return true;

        String activeProfile = matrix.getString("active-profile", "default");
        String profilePath = "profiles." + activeProfile + ".gui." + guiKey + "." + featureKey;
        if (matrix.contains(profilePath)) {
            return matrix.getBoolean(profilePath, true);
        }

        String fallbackPath = "profiles.default.gui." + guiKey + "." + featureKey;
        return matrix.getBoolean(fallbackPath, true);
    }

    public String msg(String key, String... replacements) {
        String message = null;
        if (localeConfig != null) {
            message = localeConfig.getString("messages." + key);
        }
        if (message == null) {
            message = getConfig().getString("messages." + key);
        }
        if (message == null) message = "&cMissing message: " + key;
    
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return color(message);
    }
    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
