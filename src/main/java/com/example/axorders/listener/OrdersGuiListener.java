package com.example.axorders.listener;

import com.example.axorders.AxOrdersAddon;
import com.example.axorders.gui.OrdersGUI;
import com.example.axorders.model.BuyOrder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class OrdersGuiListener implements Listener {

    private final AxOrdersAddon plugin;

    public OrdersGuiListener(AxOrdersAddon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
    
        if (!(event.getView().getTopInventory().getHolder() instanceof OrdersGUI gui)) return;
    
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
    
        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
    
        if (slot < 0 || slot >= topSize) return;
    
        event.setCancelled(true);
    
        if (slot == gui.getSlotPrevious()) {
            gui.previousPage();
            player.openInventory(gui.buildInventory());
            return;
        }
    
        if (slot == gui.getSlotSort()) {
            gui.cycleSortMode();
            player.openInventory(gui.buildInventory());
            return;
        }
    
        if (slot == gui.getSlotRefresh()) {
            player.openInventory(gui.buildInventory());
            return;
        }
    
        if (slot == gui.getSlotNext()) {
            gui.nextPage();
            player.openInventory(gui.buildInventory());
            return;
        }
    
        BuyOrder order = gui.getOrderAtSlot(slot);
        if (order == null) return;
    
        String error = event.isRightClick()
                ? plugin.getOrderManager().cancelOrder(player, order.getId())
                : plugin.getOrderManager().fillOrder(player, order.getId());
    
        if (error != null) player.sendMessage(AxOrdersAddon.color(error));
        player.openInventory(gui.buildInventory());
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OrdersGUI)) return;
    
        int topSize = event.getView().getTopInventory().getSize();
    
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }
}

