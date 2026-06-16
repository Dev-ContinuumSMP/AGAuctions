package com.example.axorders.command;

import com.example.axorders.AxOrdersAddon;
import com.example.axorders.manager.OrderManager;
import com.example.axorders.model.BuyOrder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BuyOrderCommand implements CommandExecutor, TabCompleter {

    private final AxOrdersAddon plugin;

    public BuyOrderCommand(AxOrdersAddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }
        if (!player.hasPermission("axorders.create")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (plugin.getCurrencyManager().getHook() == null) {
            player.sendMessage(plugin.msg("no-currency-hook"));
            return true;
        }   
        
        boolean handMode = args.length >= 1 && args[0].equalsIgnoreCase("hand");

        if (!handMode && args.length < 3) {
            player.sendMessage(AxOrdersAddon.color("&eUsage: /buyorder <material> <amount> <priceEach>"));
            return true;
        }

        if (handMode && args.length < 3) {
            player.sendMessage(AxOrdersAddon.color("&eUsage: /buyorder hand <amount> <priceEach>"));
            return true;
        }
            

        ItemStack template;
        
        if (handMode) {
            ItemStack hand = player.getInventory().getItemInMainHand();

            if(hand == null || hand.getType().isAir()) {
                player.sendMessage(AxOrdersAddon.color("&cHold the custom item you want to order."));
                return true;
            }

            template = hand.clone();
            template.setAmount(1);
            
        } else {
            Material material = OrderManager.parseMaterial(args[0]);

            if(material == null || material.isAir() || !material.isItem()) {
                player.sendMessage(plugin.msg("unknown-material", "{material}", args[0]));
                return true;
            }

            template = new ItemStack(material);
        }

        int amount;        
        double priceEach;
        
        try {
            amount = Integer.parseInt(args[1]);
            priceEach = Double.parseDouble(args[2]);

            if (amount <= 0 || priceEach <= 0) throw new NumberFormatException();

        } catch (Exception e) {
            player.sendMessage(plugin.msg("invalid-amount"));
            return true;
        }

        int maxOrders = plugin.getConfig().getInt("max-orders-per-player", 5);
        if (plugin.getOrderManager().getOrdersByPlayer(player.getUniqueId()).size() >= maxOrders) {
            player.sendMessage(AxOrdersAddon.color("&cYou already have &e" + maxOrders + " &copen buy orders."));
            return true;
        }

        double total = amount * priceEach;

        if (!plugin.getCurrencyManager().has(player.getUniqueId(), total)) {
            player.sendMessage(plugin.msg("not-enough-money",
                    "{amount}", plugin.getCurrencyManager().format(total)));
            return true;
        }

        if (!plugin.getCurrencyManager().take(player.getUniqueId(), total)) {
            player.sendMessage(plugin.msg("not-enough-money",
                    "{amount}", plugin.getCurrencyManager().format(total)));
            return true;
        }

        BuyOrder order = new BuyOrder(
                UUID.randomUUID(),
                player.getUniqueId(),
                player.getName(),
                template,
                amount,
                0,
                priceEach,
                System.currentTimeMillis()
        );

        if (!plugin.getOrderManager().addOrder(order)) {
            boolean refunded = plugin.getCurrencyManager().give(player.getUniqueId(), total);
            player.sendMessage(plugin.msg(refunded ? "order-save-failed" : "refund-failed"));
            return true;
        }

        player.sendMessage(plugin.msg("order-created",
                "{amount}", String.valueOf(amount),
                "{material}", OrderManager.itemName(template),
                "{price}", plugin.getCurrencyManager().format(priceEach)));

        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String typed = args[0].toLowerCase();
            
            List<String> completions = Arrays.stream(Material.values())
                    .filter(material -> material.isItem() && !material.isAir())
                    .map(material -> material.name().toLowerCase())
                    .filter(name -> name.startsWith(typed))
                    .limit(30)
                    .collect(Collectors.toList());
            
            if ("hand".startsWith(typed)) {
                completions.add(0, "hand");

            }
            
            return completions;
        }
        if (args.length == 2) return List.of("<amount>");
        if (args.length == 3) return List.of("<priceEach>");
        
        return Collections.emptyList();
    }
}
