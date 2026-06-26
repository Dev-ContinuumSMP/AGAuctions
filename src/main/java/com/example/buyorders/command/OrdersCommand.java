package com.example.buyorders.command;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.gui.CollectionGUI;
import com.example.buyorders.gui.OrdersGUI;
import com.example.buyorders.manager.OrderManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OrdersCommand implements CommandExecutor, TabCompleter {

    private final BuyOrders plugin;

    public OrdersCommand(BuyOrders plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("buyorders.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }

            plugin.reloadAllPluginConfigs();
            plugin.getCurrencyManager().reload();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }
        
        if (!player.hasPermission("buyorders.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("collect")
            || args[0].equalsIgnoreCase("collection")
            || args[0].equalsIgnoreCase("claim"))) {
            new CollectionGUI(plugin, player).open();
            return true;
        }

        Material filter = null;
        
        if (args.length > 0) {
            filter = OrderManager.parseMaterial(args[0]);
            
            if (filter == null || filter.isAir() || !filter.isItem()) {
                player.sendMessage(plugin.msg("unknown-material", "{material}", args[0]));
                return true;
            }
        }

        new OrdersGUI(plugin, player, filter).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        
        if (args.length != 1) {
            return Collections.emptyList();
        }
    
        String typed = args[0].toLowerCase();
    
        List<String> completions = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(m -> m.name().toLowerCase())
                .filter(name -> name.startsWith(typed))
                .limit(30)
                .collect(Collectors.toList());

        if ("collect".startsWith(typed)) {
            completions.add(0, "collect");
        }

        if ("claim".startsWith(typed)) {
            completions.add(0, "claim");
        }

        if (sender.hasPermission("buyorders.admin") && "reload".startsWith(typed)) {
            completions.add(0, "reload");
        }

        return completions;
    }
}
