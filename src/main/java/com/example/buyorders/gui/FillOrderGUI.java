package com.example.buyorders.gui;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.manager.OrderManager;
import com.example.buyorders.model.BuyOrder;
import com.example.buyorders.util.ItemMatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class FillOrderGUI implements InventoryHolder {

    private final BuyOrders plugin;
    private final Player player;
    private final UUID orderId;
    private final ItemStack template;
    private Inventory inventory;
    private boolean closingHandled;

    public FillOrderGUI(BuyOrders plugin, Player player, BuyOrder order) {
        this.plugin = plugin;
        this.player = player;
        this.orderId = order.getId();
        this.template = order.getItemTemplate().clone();
        this.template.setAmount(1);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(buildInventory());
    }

    public Inventory buildInventory() {
        BuyOrder order = plugin.getOrderManager().getOrder(orderId);
        if (order == null || order.isFulfilled()) {
            player.sendMessage(plugin.msg("order-not-found"));
            return Bukkit.createInventory(this, getInventorySize(), color(configString("fill-gui.title", "&6Fill Order")));
        }

        Map<String, String> placeholders = placeholders(order, 0);
        Inventory inv = Bukkit.createInventory(this, getInventorySize(), color(applyPlaceholders(configString("fill-gui.title", "&6Fill {material}"), placeholders)));
        this.inventory = inv;

        if (plugin.getConfig().getBoolean("fill-gui.filler.enabled", true)) {
            ItemStack filler = makeConfiguredItem("fill-gui.filler", placeholders);
            for (int slot : plugin.getConfig().getIntegerList("fill-gui.filler.slots")) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, filler);
            }
        }

        setControl(inv, "info", placeholders);
        setControl(inv, "confirm", placeholders);
        setControl(inv, "cancel", placeholders);
        return inv;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ItemStack getTemplate() {
        return template.clone();
    }

    public boolean isDepositSlot(int slot) {
        return getDepositSlots().contains(slot);
    }

    public boolean isConfirmSlot(int slot) {
        return slot == getControlSlot("confirm");
    }

    public boolean isCancelSlot(int slot) {
        return slot == getControlSlot("cancel");
    }

    public boolean isControlSlot(int slot) {
        return slot == getControlSlot("info") || isConfirmSlot(slot) || isCancelSlot(slot);
    }

    public boolean matchesOrder(ItemStack item) {
        return item != null && !item.getType().isAir() && ItemMatcher.matchesCustomItem(item, template);
    }

    public int addDeposit(ItemStack source) {
        if (!matchesOrder(source)) return source == null ? 0 : source.getAmount();

        int remaining = Math.min(source.getAmount(), getMaxDeposit() - countDeposited());
        int notAccepted = source.getAmount() - remaining;
        if (remaining <= 0) return source.getAmount();

        for (int slot : getDepositSlots()) {
            if (remaining <= 0) break;
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                int move = Math.min(remaining, source.getMaxStackSize());
                ItemStack copy = source.clone();
                copy.setAmount(move);
                inventory.setItem(slot, copy);
                remaining -= move;
                continue;
            }

            if (!current.isSimilar(source)) continue;
            int room = current.getMaxStackSize() - current.getAmount();
            if (room <= 0) continue;

            int move = Math.min(remaining, room);
            current.setAmount(current.getAmount() + move);
            remaining -= move;
        }

        return notAccepted + remaining;
    }

    public List<ItemStack> collectDepositedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : getDepositSlots()) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            items.add(item.clone());
        }
        return items;
    }

    public void clearDeposits() {
        for (int slot : getDepositSlots()) {
            inventory.setItem(slot, null);
        }
    }

    public void returnDeposits() {
        returnItems(collectDepositedItems());
        clearDeposits();
    }

    public void returnItems(List<ItemStack> items) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(ItemStack[]::new));
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    public void markClosingHandled() {
        closingHandled = true;
    }

    public boolean isClosingHandled() {
        return closingHandled;
    }

    public void refreshInfo() {
        BuyOrder order = plugin.getOrderManager().getOrder(orderId);
        if (order == null) return;
        Map<String, String> placeholders = placeholders(order, countDeposited());
        setControl(inventory, "info", placeholders);
        setControl(inventory, "confirm", placeholders);
        setControl(inventory, "cancel", placeholders);
    }

    private int countDeposited() {
        int count = 0;
        for (ItemStack item : collectDepositedItems()) {
            if (matchesOrder(item)) count += item.getAmount();
        }
        return count;
    }

    private int getMaxDeposit() {
        BuyOrder order = plugin.getOrderManager().getOrder(orderId);
        return order == null ? 0 : Math.max(0, order.getRemaining());
    }

    private void setControl(Inventory inv, String key, Map<String, String> placeholders) {
        int slot = getControlSlot(key);
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, makeConfiguredItem("fill-gui.controls." + key, placeholders));
    }

    private ItemStack makeConfiguredItem(String path, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(configString(path + ".material", defaultItemMaterial(path)));
        if (material == null || material.isAir()) material = Material.STONE;
        int amount = Math.max(1, Math.min(64, plugin.getConfig().getInt(path + ".amount", 1)));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color(applyPlaceholders(configString(path + ".name", defaultItemName(path)), placeholders)));

        List<String> lore = plugin.getConfig().getStringList(path + ".lore");
        if (lore.isEmpty()) lore = defaultItemLore(path);
        meta.setLore(colorLines(applyPlaceholders(lore, placeholders)));

        if ("fill-gui.controls.info".equals(path) && plugin.getConfig().getBoolean("fill-gui.item-tooltip.enabled", true)) {
            List<String> nbtLines = buildNbtLines(template);
            if (!nbtLines.isEmpty()) {
                if (plugin.getConfig().getBoolean("fill-gui.item-tooltip.separator", true) && meta.getLore() != null && !meta.getLore().isEmpty()) {
                    List<String> updatedLore = new ArrayList<>(meta.getLore());
                    updatedLore.add("");
                    updatedLore.addAll(nbtLines);
                    meta.setLore(updatedLore);
                } else if (meta.getLore() != null) {
                    List<String> updatedLore = new ArrayList<>(meta.getLore());
                    updatedLore.addAll(nbtLines);
                    meta.setLore(updatedLore);
                }
            }
        }

        if (plugin.getConfig().isSet(path + ".custom-model-data")) {
            meta.setCustomModelData(plugin.getConfig().getInt(path + ".custom-model-data"));
        }

        item.setItemMeta(meta);
        return item;
    }

    private String defaultItemMaterial(String path) {
        return switch (path) {
            case "fill-gui.controls.cancel" -> "BARRIER";
            case "fill-gui.controls.info" -> "PAPER";
            case "fill-gui.controls.confirm" -> "EMERALD_BLOCK";
            case "fill-gui.filler" -> "BLACK_STAINED_GLASS_PANE";
            default -> "STONE";
        };
    }

    private String defaultItemName(String path) {
        return switch (path) {
            case "fill-gui.controls.cancel" -> "&cCancel";
            case "fill-gui.controls.info" -> "&eDeposited: &f{deposited}&7/&f{remaining}";
            case "fill-gui.controls.confirm" -> "&aConfirm Sale";
            default -> " ";
        };
    }

    private List<String> defaultItemLore(String path) {
        return switch (path) {
            case "fill-gui.controls.cancel" -> List.of("&7Returns deposited items");
            case "fill-gui.controls.info" -> List.of("&ePrice Each: &a{price_each}", "&ePayout: &a{payout}");
            case "fill-gui.controls.confirm" -> List.of("&7Sell deposited matching items");
            default -> List.of();
        };
    }

    private Map<String, String> placeholders(BuyOrder order, int deposited) {
        double payout = deposited * order.getPriceEach();
        return Map.of(
                "{id}", order.getShortId(),
                "{buyer}", order.getBuyerName(),
                "{material}", OrderManager.materialName(order.getMaterial()),
                "{remaining}", String.valueOf(order.getRemaining()),
                "{deposited}", String.valueOf(deposited),
                "{price_each}", plugin.getCurrencyManager().format(order.getPriceEach()),
                "{payout}", plugin.getCurrencyManager().format(payout)
        );
    }

    private int getInventorySize() {
        int rows = Math.max(2, Math.min(6, plugin.getConfig().getInt("fill-gui.rows", 6)));
        return rows * 9;
    }

    private List<Integer> getDepositSlots() {
        List<Integer> slots = plugin.getConfig().getIntegerList("fill-gui.deposit-slots");
        int size = getInventorySize();
        if (slots.isEmpty()) {
            List<Integer> fallback = new ArrayList<>();
            int max = Math.min(45, size);
            for (int slot = 0; slot < max; slot++) fallback.add(slot);
            return fallback;
        }
        return slots.stream()
                .filter(slot -> slot >= 0 && slot < size)
                .filter(slot -> !isControlSlot(slot))
                .distinct()
                .toList();
    }

    private int getControlSlot(String key) {
        return plugin.getConfig().getInt("fill-gui.controls." + key + ".slot", switch (key) {
            case "info" -> 49;
            case "confirm" -> 50;
            case "cancel" -> 48;
            default -> -1;
        });
    }

    private String configString(String path, String fallback) {
        String value = plugin.getConfig().getString(path);
        return value == null ? fallback : value;
    }

    private String applyPlaceholders(String line, Map<String, String> placeholders) {
        String replaced = line.replace("{store_name}", configString("store-name", "&6&lBuy Orders"));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        return replaced;
    }

    private List<String> applyPlaceholders(List<String> lines, Map<String, String> placeholders) {
        List<String> replaced = new ArrayList<>();
        for (String line : lines) replaced.add(applyPlaceholders(line, placeholders));
        return replaced;
    }

    private String color(String input) {
        return BuyOrders.color(input);
    }

    private List<String> colorLines(List<String> lines) {
        List<String> colored = new ArrayList<>();
        for (String line : lines) colored.add(color(line));
        return colored;
    }

    private List<String> buildNbtLines(ItemStack item) {
        Map<String, String> values = ItemMatcher.describeStringPersistentData(item);
        if (values.isEmpty()) return List.of();

        List<String> lines = new ArrayList<>();
        lines.add(color("&8&m----------------"));
        lines.add(color("&bNBT Data"));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            lines.add(color("&7" + entry.getKey() + ": &f" + entry.getValue()));
        }
        lines.add(color("&8&m----------------"));
        return lines;
    }
}
