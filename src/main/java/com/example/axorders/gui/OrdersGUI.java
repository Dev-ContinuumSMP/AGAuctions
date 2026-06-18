package com.example.axorders.gui;

import com.example.axorders.AxOrdersAddon;
import com.example.axorders.manager.OrderManager;
import com.example.axorders.model.BuyOrder;
import com.example.axorders.util.ItemMatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class OrdersGUI implements InventoryHolder {

    public enum SortMode {
        PRICE_DESC, PRICE_ASC, NEWEST, OLDEST
    }

    private final AxOrdersAddon plugin;
    private final Player player;
    private final Material filter;
    private int page;
    private SortMode sortMode = SortMode.PRICE_DESC;
    private Inventory inventory;

    public OrdersGUI(AxOrdersAddon plugin, Player player, Material filter) {
        this.plugin = plugin;
        this.player = player;
        this.filter = filter;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(buildInventory());
    }

    public Inventory buildInventory() {
        List<Integer> orderSlots = getOrderSlots();
        int itemsPerPage = orderSlots.size();
        String titlePath = filter == null ? "gui.title" : "gui.filtered-title";
        Inventory inv = Bukkit.createInventory(this, getInventorySize(), color(applyGlobalPlaceholders(configString(titlePath, "{store_name}"))));
        this.inventory = inv;

        List<BuyOrder> orders = getOrders();
        int totalPages = Math.max(1, (int) Math.ceil(orders.size() / (double) itemsPerPage));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, orders.size());
        for (int index = start; index < end; index++) {
            inv.setItem(orderSlots.get(index - start), buildOrderItem(orders.get(index)));
        }

        if (plugin.getConfig().getBoolean("gui.filler.enabled", true)) {
            ItemStack filler = makeConfiguredItem("gui.filler", Map.of());
            for (int slot : plugin.getConfig().getIntegerList("gui.filler.slots")) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, filler);
            }
        }

        Map<String, String> pagePlaceholders = Map.of(
                "{page}", String.valueOf(page + 1),
                "{total_pages}", String.valueOf(totalPages),
                "{order_count}", String.valueOf(orders.size()),
                "{sort}", sortLabel()
        );

        if (page > 0) setControl(inv, "previous", pagePlaceholders);
        setControl(inv, "sort", pagePlaceholders);
        setControl(inv, "info", pagePlaceholders);
        setControl(inv, "refresh", pagePlaceholders);
        if (page < totalPages - 1) setControl(inv, "next", pagePlaceholders);

        return inv;
    }

    private ItemStack buildOrderItem(BuyOrder order) {
        int amount = Math.min(order.getRemaining(), 64);
        ItemStack item = order.getItemTemplate().clone();
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        boolean preserveName = plugin.getConfig().getBoolean("gui.order-item.preserve-template-name", true);
        if (!preserveName || !meta.hasDisplayName()) {
            meta.setDisplayName(color(applyOrderPlaceholders(
                    configString("gui.order-item.name", "&b{material} &8#{id}"),
                    order,
                    0,
                    0
            )));
        }

        item.setItemMeta(meta);

        int available = countMatchingItems(order.getItemTemplate());
        int fillAmount = Math.min(available, order.getRemaining());
        double payout = fillAmount * order.getPriceEach();

        List<String> lore = colorLines(applyOrderPlaceholders(
                plugin.getConfig().getStringList("gui.order-item.lore"),
                order,
                available,
                payout
        ));

        if (order.getBuyerUuid().equals(player.getUniqueId())) {
            lore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.own-order"),
                    order,
                    available,
                    payout
            )));
        } else if (fillAmount > 0) {
            lore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.can-fill"),
                    order,
                    available,
                    payout
            ).stream().map(line -> line.replace("{fill_amount}", String.valueOf(fillAmount))).toList()));
        } else {
            lore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.no-items"),
                    order,
                    available,
                    payout
            )));
        }
        if (player.hasPermission("axorders.admin") && !order.getBuyerUuid().equals(player.getUniqueId())) {
            lore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.admin"),
                    order,
                    available,
                    payout
            )));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<BuyOrder> getOrders() {
        List<BuyOrder> orders = filter == null
                ? plugin.getOrderManager().getOrders()
                : plugin.getOrderManager().getOrdersByMaterial(filter);

        switch (sortMode) {
            case PRICE_DESC -> orders.sort(Comparator.comparingDouble(BuyOrder::getPriceEach).reversed());
            case PRICE_ASC -> orders.sort(Comparator.comparingDouble(BuyOrder::getPriceEach));
            case NEWEST -> orders.sort(Comparator.comparingLong(BuyOrder::getCreatedAt).reversed());
            case OLDEST -> orders.sort(Comparator.comparingLong(BuyOrder::getCreatedAt));
        }
        return orders;
    }

    private int countMatchingItems(ItemStack template) {
        int count = 0;
    
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && ItemMatcher.matchesCustomItem(item, template)) {
                count += item.getAmount();
            }
        }
    
        return count;
    }

    private void setControl(Inventory inv, String key, Map<String, String> placeholders) {
        int slot = getControlSlot(key);
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, makeConfiguredItem("gui.controls." + key, placeholders));
    }

    private ItemStack makeConfiguredItem(String path, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(configString(path + ".material", "STONE"));
        if (material == null || material.isAir()) material = Material.STONE;
        int amount = Math.max(1, Math.min(64, plugin.getConfig().getInt(path + ".amount", 1)));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color(applyPlaceholders(configString(path + ".name", " "), placeholders)));
        meta.setLore(colorLines(applyPlaceholders(plugin.getConfig().getStringList(path + ".lore"), placeholders)));

        if (plugin.getConfig().isSet(path + ".custom-model-data")) {
            meta.setCustomModelData(plugin.getConfig().getInt(path + ".custom-model-data"));
        }

        item.setItemMeta(meta);
        return item;
    }

    private String sortLabel() {
        String key = switch (sortMode) {
            case PRICE_DESC -> "Highest Price";
            case PRICE_ASC -> "Lowest Price";
            case NEWEST -> "Newest";
            case OLDEST -> "Oldest";
        };
        return configString("gui.sort-labels." + sortMode.name().toLowerCase(), key);
    }

    public BuyOrder getOrderAtSlot(int slot) {
        List<Integer> orderSlots = getOrderSlots();
        int slotIndex = orderSlots.indexOf(slot);
        if (slotIndex < 0) return null;
        int index = page * orderSlots.size() + slotIndex;
        List<BuyOrder> orders = getOrders();
        return index >= orders.size() ? null : orders.get(index);
    }

    public void previousPage() {
        if (page > 0) page--;
    }

    public void nextPage() {
        int totalPages = Math.max(1, (int) Math.ceil(getOrders().size() / (double) getOrderSlots().size()));
        if (page < totalPages - 1) page++;
    }

    public void cycleSortMode() {
        SortMode[] modes = SortMode.values();
        sortMode = modes[(sortMode.ordinal() + 1) % modes.length];
        page = 0;
    }

    public int getSlotPrevious() { return getControlSlot("previous"); }
    public int getSlotSort() { return getControlSlot("sort"); }
    public int getSlotRefresh() { return getControlSlot("refresh"); }
    public int getSlotNext() { return getControlSlot("next"); }

    private int getInventorySize() {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("gui.rows", 6)));
        return rows * 9;
    }

    private List<Integer> getOrderSlots() {
        List<Integer> slots = plugin.getConfig().getIntegerList("gui.order-slots");
        int size = getInventorySize();
        if (slots.isEmpty()) {
            int max = Math.min(45, size);
            for (int slot = 0; slot < max; slot++) slots.add(slot);
        }
        List<Integer> configuredSlots = slots.stream()
                .filter(slot -> slot >= 0 && slot < size)
                .distinct()
                .toList();
        if (!configuredSlots.isEmpty()) return configuredSlots;

        List<Integer> fallbackSlots = new ArrayList<>();
        int max = Math.min(45, size);
        for (int slot = 0; slot < max; slot++) fallbackSlots.add(slot);
        return fallbackSlots;
    }

    private int getControlSlot(String key) {
        return plugin.getConfig().getInt("gui.controls." + key + ".slot", switch (key) {
            case "previous" -> 45;
            case "sort" -> 46;
            case "info" -> 49;
            case "refresh" -> 50;
            case "next" -> 53;
            default -> -1;
        });
    }

    private String configString(String path, String fallback) {
        String value = plugin.getConfig().getString(path);
        return value == null ? fallback : value;
    }

    private String applyGlobalPlaceholders(String line) {
        return line
                .replace("{store_name}", configString("store-name", "&6&lBuy Orders"))
                .replace("{filter}", filter == null ? "" : OrderManager.materialName(filter));
    }

    private List<String> applyPlaceholders(List<String> lines, Map<String, String> placeholders) {
        List<String> replaced = new ArrayList<>();
        for (String line : lines) replaced.add(applyPlaceholders(line, placeholders));
        return replaced;
    }

    private String applyPlaceholders(String line, Map<String, String> placeholders) {
        String replaced = applyGlobalPlaceholders(line);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        return replaced;
    }

    private List<String> applyOrderPlaceholders(List<String> lines, BuyOrder order, int available, double payout) {
        List<String> replaced = new ArrayList<>();
        for (String line : lines) replaced.add(applyOrderPlaceholders(line, order, available, payout));
        return replaced;
    }

    private String applyOrderPlaceholders(String line, BuyOrder order, int available, double payout) {
        return applyGlobalPlaceholders(line)
                .replace("{id}", order.getShortId())
                .replace("{buyer}", order.getBuyerName())
                .replace("{material}", OrderManager.materialName(order.getMaterial()))
                .replace("{remaining}", String.valueOf(order.getRemaining()))
                .replace("{wanted}", String.valueOf(order.getQuantityWanted()))
                .replace("{filled}", String.valueOf(order.getQuantityFilled()))
                .replace("{price_each}", plugin.getCurrencyManager().format(order.getPriceEach()))
                .replace("{your_items}", String.valueOf(available))
                .replace("{payout}", plugin.getCurrencyManager().format(payout));
    }

    private String color(String input) {
        return AxOrdersAddon.color(input);
    }

    private List<String> colorLines(List<String> lines) {
        List<String> colored = new ArrayList<>();
        for (String line : lines) colored.add(color(line));
        return colored;
    }
}
