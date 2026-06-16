package com.example.axorders.command;

import com.example.axorders.AxOrdersAddon;
import com.example.axorders.gui.OrdersGUI;
import com.example.axorders.manager.OrderManager;
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

    private final AxOrdersAddon plugin;

    public OrdersCommand(AxOrdersAddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }
        
        if (!player.hasPermission("axorders.use")) {
            player.sendMessage(plugin.msg("no-permission"));
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
        
        if (args.length != 1) return Collections.emptyList();
    
        String typed = args[0].toLowerCase();
    
        return Arrays.stream(Material.values())
                .filter(m -> m.isItem() && !m.isAir())
                .map(m -> m.name().toLowerCase())
                .filter(name -> name.startsWith(typed))
                .limit(30)
                .collect(Collectors.toList());
    }
}
