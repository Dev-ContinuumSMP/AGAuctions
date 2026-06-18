package com.example.axorders.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoryUtils {

    public static int countMatching(Player player, ItemStack template) {

        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {

            if (item == null) continue;

            if (ItemMatcher.matchesCustomItem(item, template)) {
                count += item.getAmount();
            }
        }

        return count;
    }

    public static boolean removeMatching(Player player, ItemStack template, int amount) {

        if (countMatching(player, template) < amount) {
            return false;
        }

        int remaining = amount;

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {

            ItemStack item = player.getInventory().getItem(slot);

            if (item == null) continue;

            if (!ItemMatcher.matchesCustomItem(item, template)) {
                continue;
            }

            int take = Math.min(item.getAmount(), remaining);

            item.setAmount(item.getAmount() - take);

            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }

            remaining -= take;

            if (remaining <= 0) {
                player.updateInventory();
                return true;
            }
        }

        return false;
    }
}