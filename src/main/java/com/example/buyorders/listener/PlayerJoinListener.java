package com.example.buyorders.listener;

import com.example.buyorders.BuyOrders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final BuyOrders plugin;

    public PlayerJoinListener(BuyOrders plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
    
        Bukkit.getScheduler().runTask(plugin, () -> {
            int pending = plugin.getOrderManager().getPendingDeliveryCount(player.getUniqueId());
            if (pending <= 0) return;

            player.sendMessage(BuyOrders.color(plugin.msg("collection-pending", "{amount}", String.valueOf(pending))));
        });
    }
}
