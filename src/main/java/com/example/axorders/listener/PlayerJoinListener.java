package com.example.axorders.listener;

import com.example.axorders.AxOrdersAddon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerJoinListener implements Listener {

    private final AxOrdersAddon plugin;

    public PlayerJoinListener(AxOrdersAddon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
    
        Bukkit.getScheduler().runTask(plugin, () -> {
    
            List<ItemStack> pending = plugin.getOrderManager()
                    .getPendingDeliveries(player.getUniqueId());
    
            if (pending == null || pending.isEmpty()) return;
    
            boolean overflowed = false;
    
            for (ItemStack item : pending) {
                if (item == null || item.getType().isAir()) continue;
    
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
    
                if (!overflow.isEmpty()) {
                    overflowed = true;
                    for (ItemStack overflowItem : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                    }
                }
            }
    
            plugin.getOrderManager().clearPendingDeliveries(player.getUniqueId());
    
            if (overflowed) {
                player.sendMessage(AxOrdersAddon.color("&eSome buy-order items were dropped because your inventory was full."));
            } else {
                player.sendMessage(AxOrdersAddon.color("&aYour pending buy-order items were delivered."));
            }
        });
    }
