package com.example.buyorders.listener;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.gui.AuctionHouseGUI;
import com.example.buyorders.gui.CollectionGUI;
import com.example.buyorders.gui.FillOrderGUI;
import com.example.buyorders.gui.OrdersGUI;
import com.example.buyorders.gui.SearchGUI;
import com.example.buyorders.model.AuctionListing;
import com.example.buyorders.model.BuyOrder;
import com.example.buyorders.manager.OrderManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class OrdersGuiListener implements Listener {

    private final BuyOrders plugin;

    public OrdersGuiListener(BuyOrders plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof FillOrderGUI fillGui) {
            handleFillGuiClick(event, player, fillGui);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof CollectionGUI collectionGui) {
            handleCollectionGuiClick(event, player, collectionGui);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AuctionHouseGUI auctionGui) {
            handleAuctionGuiClick(event, player, auctionGui);
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof OrdersGUI gui)) return;
    
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
    
        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
    
        if (slot < 0 || slot >= topSize) return;
    
        event.setCancelled(true);

        // Close should always win if config maps multiple controls to the same slot.
        if (slot == gui.getSlotClose()) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
    
        if (slot == gui.getSlotSort()) {
            gui.cycleSortMode();
            player.openInventory(gui.buildInventory());
            return;
        }

        if (slot == gui.getSlotSearch()) {
            if (event.isRightClick()) {
                if (gui.hasSearchQuery()) {
                    gui.clearSearchQuery();
                    player.sendMessage(plugin.msg("search-cleared"));
                }
                new OrdersGUI(plugin, player, null).open();
                return;
            }

            new SearchGUI(plugin, player, gui).open();
            return;
        }
    
        if (slot == gui.getSlotRefresh()) {
            player.openInventory(gui.buildInventory());
            return;
        }

        if (slot == gui.getSlotCollection()) {
            new CollectionGUI(plugin, player).open();
            return;
        }
    
        if (slot == gui.getSlotNext()) {
            if (event.isRightClick()) {
                gui.previousPage();
            } else {
                gui.nextPage();
            }
            player.openInventory(gui.buildInventory());
            return;
        }
    
        BuyOrder order = gui.getOrderAtSlot(slot);
        if (order == null) return;
    
        String error = event.isRightClick()
                ? plugin.getOrderManager().cancelOrder(player, order.getId())
                : null;

        if (error == null && !event.isRightClick()) {
            new FillOrderGUI(plugin, player, order).open();
            return;
        }
    
        if (error != null) player.sendMessage(BuyOrders.color(error));
        player.openInventory(gui.buildInventory());
    }

    private void handleCollectionGuiClick(InventoryClickEvent event, Player player, CollectionGUI gui) {
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (slot < 0 || slot >= topSize) return;

        event.setCancelled(true);

        if (slot == gui.getSlotBack()) {
            new OrdersGUI(plugin, player, null).open();
            return;
        }

        if (slot == gui.getSlotClaimAll()) {
            gui.claimAll();
            return;
        }

        if (slot == gui.getSlotNext()) {
            if (event.isRightClick()) {
                gui.previousPage();
            } else {
                gui.nextPage();
            }
            player.openInventory(gui.buildInventory());
            return;
        }

        gui.claimSlot(slot);
    }

    private void handleAuctionGuiClick(InventoryClickEvent event, Player player, AuctionHouseGUI gui) {
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (slot < 0 || slot >= topSize) return;

        event.setCancelled(true);

        if (slot == gui.getSlotClose()) {
            player.closeInventory();
            return;
        }

        if (slot == gui.getSlotSort()) {
            gui.cycleSortMode();
            player.openInventory(gui.buildInventory());
            return;
        }

        if (slot == gui.getSlotSearch()) {
            if (event.isRightClick()) {
                if (gui.hasSearchQuery()) {
                    gui.clearSearchQuery();
                    player.sendMessage(plugin.msg("search-cleared"));
                }
                new AuctionHouseGUI(plugin, player).open();
                return;
            }

            new SearchGUI(plugin, player, gui).open();
            return;
        }

        if (slot == gui.getSlotClaim()) {
            int claimed = plugin.getAuctionHouseManager().claimAll(player);
            if (claimed <= 0) {
                player.sendMessage(BuyOrders.color("&eYou do not have any AH claims waiting."));
            } else {
                player.sendMessage(BuyOrders.color("&aClaimed &e" + claimed + " &aAH item stack(s)."));
            }
            player.openInventory(gui.buildInventory());
            return;
        }

        if (slot == gui.getSlotNext()) {
            if (event.isRightClick()) {
                gui.previousPage();
            } else {
                gui.nextPage();
            }
            player.openInventory(gui.buildInventory());
            return;
        }

        AuctionListing listing = gui.getListingAtSlot(slot);
        if (listing == null) return;

        boolean admin = player.hasPermission("buyorders.admin");
        if (event.isRightClick()) {
            var result = plugin.getAuctionHouseManager().cancelListing(player, listing.getId(), admin);
            if (result.error() != null) {
                player.sendMessage(BuyOrders.color(result.error()));
            } else {
                player.sendMessage(BuyOrders.color(result.returnedToInventory()
                        ? "&aListing cancelled and item returned."
                        : "&aListing cancelled. Item moved to AH claims."));
            }
            player.openInventory(gui.buildInventory());
            return;
        }

        if (listing.isAuction()) {
            double bidAmount = plugin.getAuctionHouseManager().getMinimumNextBid(listing);
            var bidResult = plugin.getAuctionHouseManager().placeBid(player, listing.getId(), bidAmount);
            if (bidResult.error() != null) {
                player.sendMessage(BuyOrders.color(bidResult.error()));
            } else {
                player.sendMessage(BuyOrders.color(bidResult.extended()
                        ? "&aBid placed at &e" + plugin.getCurrencyManager().format(bidResult.bidAmount()) + "&a. Auction was extended."
                        : "&aBid placed at &e" + plugin.getCurrencyManager().format(bidResult.bidAmount()) + "&a."));
            }
            player.openInventory(gui.buildInventory());
            return;
        }

        var result = plugin.getAuctionHouseManager().purchaseListing(player, listing.getId());
        if (result.error() != null) {
            player.sendMessage(BuyOrders.color(result.error()));
        } else {
            player.sendMessage(BuyOrders.color(result.deliveredToInventory()
                    ? "&aPurchased listing successfully."
                    : "&aPurchased listing. Item moved to AH claims."));
        }
        player.openInventory(gui.buildInventory());
    }

    private void handleFillGuiClick(InventoryClickEvent event, Player player, FillOrderGUI gui) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();

        if (clicked == null) {
            event.setCancelled(true);
            return;
        }

        if (clicked != top) {
            if (!event.isShiftClick()) return;

            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (!gui.matchesOrder(clickedItem)) {
                player.sendMessage(plugin.msg("fill-invalid-item"));
                return;
            }

            int leftover = gui.addDeposit(clickedItem);
            if (leftover <= 0) {
                event.setCurrentItem(null);
            } else {
                clickedItem.setAmount(leftover);
                event.setCurrentItem(clickedItem);
            }
            gui.refreshInfo();
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        if (gui.isConfirmSlot(slot)) {
            OrderManager.FillResult result = plugin.getOrderManager().fillOrder(player, gui.getOrderId(), gui.collectDepositedItems());
            if (result.error() != null) {
                player.sendMessage(BuyOrders.color(result.error()));
                if (plugin.getOrderManager().getOrder(gui.getOrderId()) == null) {
                    gui.returnDeposits();
                    gui.markClosingHandled();
                    player.closeInventory();
                }
                return;
            }

            gui.clearDeposits();
            gui.returnItems(result.rejectedItems());
            gui.markClosingHandled();
            player.closeInventory();
            return;
        }

        if (gui.isCancelSlot(slot)) {
            gui.returnDeposits();
            gui.markClosingHandled();
            player.closeInventory();
            return;
        }

        if (!gui.isDepositSlot(slot)) return;

        ItemStack current = top.getItem(slot);
        ItemStack cursor = event.getCursor();

        if (cursor == null || cursor.getType().isAir()) {
            if (current == null || current.getType().isAir()) return;
            player.setItemOnCursor(current);
            top.setItem(slot, null);
            gui.refreshInfo();
            return;
        }

        if (!gui.matchesOrder(cursor)) {
            player.sendMessage(plugin.msg("fill-invalid-item"));
            return;
        }

        int leftover = gui.addDeposit(cursor);
        if (leftover <= 0) {
            player.setItemOnCursor(null);
        } else {
            cursor.setAmount(leftover);
            player.setItemOnCursor(cursor);
        }
        gui.refreshInfo();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FillOrderGUI gui) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().noneMatch(slot -> slot < topSize)) return;

            event.setCancelled(true);
            if (event.getOldCursor() != null && !event.getOldCursor().getType().isAir() && !gui.matchesOrder(event.getOldCursor())) {
                event.getWhoClicked().sendMessage(plugin.msg("fill-invalid-item"));
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof CollectionGUI) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AuctionHouseGUI) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof OrdersGUI)) return;
    
        int topSize = event.getView().getTopInventory().getSize();
    
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof FillOrderGUI gui) {
            if (gui.isClosingHandled()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                gui.returnDeposits();
                player.updateInventory();
            });
            return;
        }

        SearchGUI searchGui = SearchGUI.getActive(player);
        if (searchGui != null) {
            searchGui.cleanup();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {
        SearchGUI.handleSignChange(event);
    }

}
