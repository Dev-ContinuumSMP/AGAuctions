package com.example.axorders;

import com.example.axorders.command.BuyOrderCommand;
import com.example.axorders.command.OrdersCommand;
import com.example.axorders.listener.OrdersGuiListener;
import com.example.axorders.listener.PlayerJoinListener;
import com.example.axorders.manager.CurrencyManager;
import com.example.axorders.manager.OrderManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class AxOrdersAddon extends JavaPlugin {

    private OrderManager orderManager;
    private CurrencyManager currencyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        currencyManager = new CurrencyManager(this);
        currencyManager.init();
        
        if (currencyManager.getHook() == null) {
            getLogger().warning("No economy hook found! Disabling currency features.");
        }
        
        orderManager = new OrderManager(this);
        orderManager.load();

        //Listeners
        register(new OrdersGuiListener(this));
        register(new PlayerJoinListener(this));

        //Commands
        OrdersCommand ordersCommand = new OrdersCommand(this);
        BuyOrderCommand buyOrderCommand = new BuyOrderCommand(this);
        
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
        
        getLogger().info("AxOrdersAddon enabled.");
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        if (orderManager != null) orderManager.save();
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public String msg(String key, String... replacements) {
        String message = getConfig().getString("messages." + key);
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
