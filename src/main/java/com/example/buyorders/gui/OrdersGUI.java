package com.example.buyorders.gui;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.manager.OrderManager;
import com.example.buyorders.model.BuyOrder;
import com.example.buyorders.util.ItemMatcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class OrdersGUI implements InventoryHolder {

    public enum SortMode {
        HIGHEST_PRICE, LOWEST_PRICE, RECENTLY_LISTED
    }

    private final BuyOrders plugin;
    private final Player player;
    private final Material filter;
    private String searchQuery = "";
    private int page;
    private SortMode sortMode = SortMode.HIGHEST_PRICE;
    //private SortMode sortMode = SortMode.PRICE_DESC;
    private Inventory inventory;

    public OrdersGUI(BuyOrders plugin, Player player, Material filter) {
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
        List<BuyOrder> orders = getOrders();
        int totalPages = Math.max(1, (int) Math.ceil(orders.size() / (double) itemsPerPage));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Map<String, String> pagePlaceholders = Map.of(
            "{page}", String.valueOf(page + 1),
            "{total_pages}", String.valueOf(totalPages),
            "{order_count}", String.valueOf(orders.size()),
            "{pending_count}", String.valueOf(plugin.getOrderManager().getPendingDeliveryCount(player.getUniqueId())),
            "{sort}", sortLabel(),
            "{search}", searchLabel(),
            "{highest_price}", sortLine(SortMode.HIGHEST_PRICE),
            "{lowest_price}", sortLine(SortMode.LOWEST_PRICE),
            "{recently_listed}", sortLine(SortMode.RECENTLY_LISTED)
        );

        String titlePath = filter == null ? "gui.title" : "gui.filtered-title";
        Inventory inv = Bukkit.createInventory(this, getInventorySize(), color(applyPlaceholders(applyGlobalPlaceholders(configString(titlePath, "{store_name}")), pagePlaceholders)));
        this.inventory = inv;

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, orders.size());
        for (int index = start; index < end; index++) {
            inv.setItem(orderSlots.get(index - start), buildOrderItem(orders.get(index)));
        }

        if (plugin.getConfig().getBoolean("gui.filler.enabled", true)) {
            ItemStack filler = makeConfiguredItem("gui.filler", Map.of());
            for (int slot : parseConfigSlots("gui.filler.slots")) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, filler);
            }
        }

        // Legacy placeholders kept for backward-compatible configs.
        pagePlaceholders = new java.util.HashMap<>(pagePlaceholders);
        pagePlaceholders.put("{most_per_item}", sortLine(SortMode.HIGHEST_PRICE));
        pagePlaceholders.put("{most_paid}", sortLine(SortMode.LOWEST_PRICE));
        pagePlaceholders.put("{newest}", sortLine(SortMode.RECENTLY_LISTED));
        pagePlaceholders.put("{Newest}", sortLine(SortMode.RECENTLY_LISTED));
        pagePlaceholders.put("{oldest}", sortLine(SortMode.RECENTLY_LISTED));
        pagePlaceholders.put("{Oldest}", sortLine(SortMode.RECENTLY_LISTED));

        // Cross-GUI aliases to prevent raw placeholder text when configs are mixed.
        pagePlaceholders.put("{highest_amount}", sortLine(SortMode.HIGHEST_PRICE));
        pagePlaceholders.put("{lowest_amount}", sortLine(SortMode.LOWEST_PRICE));
        pagePlaceholders.put("{recently_added}", sortLine(SortMode.RECENTLY_LISTED));

        if (plugin.isGuiFeatureEnabled("orders", "collection")) setControl(inv, "collection", pagePlaceholders);
        if (plugin.isGuiFeatureEnabled("orders", "sort")) setControl(inv, "sort", pagePlaceholders);
        if (plugin.isGuiFeatureEnabled("orders", "search")) setControl(inv, "search", pagePlaceholders);
        if (plugin.isGuiFeatureEnabled("orders", "refresh")) setControl(inv, "refresh", pagePlaceholders);
        if (plugin.isGuiFeatureEnabled("orders", "close")) setControl(inv, "close", pagePlaceholders);
        if (plugin.isGuiFeatureEnabled("orders", "paging")) setControl(inv, "next", pagePlaceholders);

        return inv;
    }

    private ItemStack buildOrderItem(BuyOrder order) {
        int amount = Math.min(order.getRemaining(), 64);
        ItemStack item = order.getItemTemplate().clone();
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        boolean inheritTemplateTooltip = plugin.getConfig().getBoolean("gui.order-item.inherit-template-tooltip", false);
        
        boolean preserveName = plugin.getConfig().getBoolean("gui.order-item.preserve-template-name", true);
        if (!inheritTemplateTooltip && (!preserveName || !meta.hasDisplayName())) {
            meta.setDisplayName(color(applyOrderPlaceholders(
                    configString("gui.order-item.name", "&b{material} &8#{id}"),
                    order,
                    0,
                    0
            )));
        }

        item.setItemMeta(meta);

        int available = countMatchingItems(order.getItemTemplate());
        int maxFillAmount = Math.min(available, order.getRemaining());
        double payout = maxFillAmount * order.getPriceEach();

        List<String> orderLoreTemplate = new ArrayList<>();
        if (!inheritTemplateTooltip && plugin.getConfig().getBoolean("gui.order-item.template-tooltip.enabled", false)) {
            orderLoreTemplate.addAll(buildTemplateTooltipLines(order.getItemTemplate()));
            if (!orderLoreTemplate.isEmpty() && plugin.getConfig().getBoolean("gui.order-item.template-tooltip.separator", true)) {
                orderLoreTemplate.add("");
            }
        }
        orderLoreTemplate.addAll(plugin.getConfig().getStringList("gui.order-item.lore"));

        List<String> orderLore = colorLines(applyOrderPlaceholders(
                orderLoreTemplate,
                order,
                available,
                payout
        ));

        if (order.getBuyerUuid().equals(player.getUniqueId())) {
            orderLore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.own-order"),
                    order,
                    available,
                    payout
            )));
        } else if (maxFillAmount > 0) {
            orderLore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.can-fill"),
                    order,
                    available,
                    payout
            ).stream().map(line -> line.replace("{fill_amount}", String.valueOf(maxFillAmount))).toList()));
        } else {
            orderLore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.no-items"),
                    order,
                    available,
                    payout
            )));
        }
        if (player.hasPermission("buyorders.admin") && !order.getBuyerUuid().equals(player.getUniqueId())) {
            orderLore.addAll(colorLines(applyOrderPlaceholders(
                    plugin.getConfig().getStringList("gui.order-item.actions.admin"),
                    order,
                    available,
                    payout
            )));
        }

        List<String> finalLore;
        if (inheritTemplateTooltip) {
            finalLore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                finalLore.addAll(meta.getLore());
                if (!orderLore.isEmpty()) {
                    finalLore.add("");
                }
            }
            finalLore.addAll(orderLore);
        } else {
            finalLore = orderLore;
        }

        meta.setLore(finalLore);
        item.setItemMeta(meta);
        return item;
    }

    private List<BuyOrder> getOrders() {
        List<BuyOrder> orders = new ArrayList<>(filter == null
                ? plugin.getOrderManager().getOrders()
            : plugin.getOrderManager().getOrdersByMaterial(filter));

        String normalizedSearch = searchQuery.trim().toLowerCase();
        if (!normalizedSearch.isEmpty()) {
            orders.removeIf(order -> !matchesSearch(order, normalizedSearch));
        }

        switch (sortMode) {
            case HIGHEST_PRICE -> orders.sort(Comparator.comparingDouble(BuyOrder::getPriceEach).reversed());
            case LOWEST_PRICE -> orders.sort(Comparator.comparingDouble(BuyOrder::getPriceEach));
            case RECENTLY_LISTED -> orders.sort(Comparator.comparingLong(BuyOrder::getCreatedAt).reversed());
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
        Material material = Material.matchMaterial(configString(path + ".material", defaultControlMaterial(path)));
        if (material == null || material.isAir()) material = Material.STONE;
        int amount = Math.max(1, Math.min(64, plugin.getConfig().getInt(path + ".amount", 1)));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color(applyPlaceholders(configString(path + ".name", defaultControlName(path)), placeholders)));

        List<String> lore = plugin.getConfig().getStringList(path + ".lore");
        if (lore.isEmpty()) lore = defaultControlLore(path);
        meta.setLore(colorLines(applyPlaceholders(lore, placeholders)));

        if (plugin.getConfig().isSet(path + ".custom-model-data")) {
            meta.setCustomModelData(plugin.getConfig().getInt(path + ".custom-model-data"));
        }

        item.setItemMeta(meta);
        return item;
    }

    private String defaultControlMaterial(String path) {
        return switch (path) {
            case "gui.controls.sort" -> "ANVIL";
            case "gui.controls.search" -> "SPYGLASS";
            case "gui.controls.collection" -> "ENDER_CHEST";
            case "gui.controls.close" -> "BARRIER";
            case "gui.controls.next" -> "ARROW";
            case "gui.filler" -> "BLACK_STAINED_GLASS_PANE";
            default -> "STONE";
        };
    }

    private String defaultControlName(String path) {
        return switch (path) {
            case "gui.controls.sort" -> "&bFilter: &f{sort}";
            case "gui.controls.search" -> "&bSearch: &f{search}";
            case "gui.controls.collection" -> "&bCollection";
            case "gui.controls.close" -> "&bClose";
            case "gui.controls.next" -> "&bChange Page";
            default -> " ";
        };
    }

    private List<String> defaultControlLore(String path) {
        return switch (path) {
            case "gui.controls.sort" -> List.of("&7Click to change", "{highest_price}", "{lowest_price}", "{recently_listed}");
            case "gui.controls.collection" -> List.of("&7{pending_count} item stack(s) waiting", "&7Click to open");
            case "gui.controls.search" -> List.of("&7Left-click to search", "&7Right-click to clear");
            case "gui.controls.close" -> List.of("&7Click to close");
            case "gui.controls.next" -> List.of("&7Left-click \u00bb Next Page", "&7Right-click \u00bb Previous Page");
            default -> List.of();
        };
    }

    private String searchLabel() {
        if (searchQuery.trim().isEmpty()) {
            return configString("gui.search-empty", "None");
        }
        return searchQuery;
    }

    private boolean matchesSearch(BuyOrder order, String normalizedSearch) {
        if (order.getBuyerName().toLowerCase().contains(normalizedSearch)
                || order.getShortId().toLowerCase().contains(normalizedSearch)
                || OrderManager.materialName(order.getMaterial()).toLowerCase().contains(normalizedSearch)
                || order.getMaterial().name().toLowerCase().contains(normalizedSearch)) {
            return true;
        }

        ItemMeta meta = order.getItemTemplate().getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).toLowerCase().contains(normalizedSearch)) {
                return true;
            }

            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    if (ChatColor.stripColor(line).toLowerCase().contains(normalizedSearch)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String sortLabel() {
        String key = switch (sortMode) {
            case HIGHEST_PRICE -> "Highest Price";
            case LOWEST_PRICE -> "Lowest Price";
            case RECENTLY_LISTED -> "Recently Listed";
        };
        return switch (sortMode) {
            case HIGHEST_PRICE -> configStringWithAliases(key,
                    "gui.sort-labels.highest_price",
                    "gui.sort-labels.price_desc",
                    "gui.sort-labels.most_per_item");
            case LOWEST_PRICE -> configStringWithAliases(key,
                    "gui.sort-labels.lowest_price",
                    "gui.sort-labels.price_asc",
                    "gui.sort-labels.most_paid");
            case RECENTLY_LISTED -> configStringWithAliases(key,
                    "gui.sort-labels.recently_listed",
                    "gui.sort-labels.newest");
        };
    }

    private String sortLine(SortMode mode) {
        String prefix = mode == sortMode ? "&f• " : "&8• ";
        String color = mode == sortMode ? "&f" : "&7";
        return prefix + color + sortLabel(mode);
    }

    private String sortLabel(SortMode mode) {
        String key = switch (mode) {
            case HIGHEST_PRICE -> "Highest Price";
            case LOWEST_PRICE -> "Lowest Price";
            case RECENTLY_LISTED -> "Recently Listed";
        };
        return switch (mode) {
            case HIGHEST_PRICE -> configStringWithAliases(key,
                    "gui.sort-labels.highest_price",
                    "gui.sort-labels.price_desc",
                    "gui.sort-labels.most_per_item");
            case LOWEST_PRICE -> configStringWithAliases(key,
                    "gui.sort-labels.lowest_price",
                    "gui.sort-labels.price_asc",
                    "gui.sort-labels.most_paid");
            case RECENTLY_LISTED -> configStringWithAliases(key,
                    "gui.sort-labels.recently_listed",
                    "gui.sort-labels.newest");
        };
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
    public int getSlotSort() { return plugin.isGuiFeatureEnabled("orders", "sort") ? getControlSlot("sort") : -1; }
    public int getSlotSearch() { return plugin.isGuiFeatureEnabled("orders", "search") ? getControlSlot("search") : -1; }
    public int getSlotRefresh() { return plugin.isGuiFeatureEnabled("orders", "refresh") ? getControlSlot("refresh") : -1; }
    public int getSlotCollection() { return plugin.isGuiFeatureEnabled("orders", "collection") ? getControlSlot("collection") : -1; }
    public int getSlotClose() { return plugin.isGuiFeatureEnabled("orders", "close") ? getControlSlot("close") : -1; }
    public int getSlotNext() { return plugin.isGuiFeatureEnabled("orders", "paging") ? getControlSlot("next") : -1; }

    public boolean hasSearchQuery() {
        return !searchQuery.trim().isEmpty();
    }

    public void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query.trim();
        page = 0;
    }

    public void clearSearchQuery() {
        searchQuery = "";
        page = 0;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    private int getInventorySize() {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("gui.rows", 6)));
        return rows * 9;
    }

    private List<Integer> parseConfigSlots(String path) {
        List<?> raw = plugin.getConfig().getList(path);
        if (raw == null || raw.isEmpty()) return List.of();
        List<Integer> slots = new ArrayList<>();
        int size = getInventorySize();
        for (Object o : raw) {
            String text = o.toString().trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$").matcher(text);
            if (m.matches()) {
                int s = Integer.parseInt(m.group(1));
                int e = Integer.parseInt(m.group(2));
                for (int slot = s; slot <= e; slot++) if (slot >= 0 && slot < size) slots.add(slot);
                continue;
            }
            try {
                int slot = Integer.parseInt(text);
                if (slot >= 0 && slot < size) slots.add(slot);
            } catch (NumberFormatException ignored) {}
        }
        return slots.stream().distinct().toList();
    }

    private List<Integer> getOrderSlots() {
        List<Integer> slots = new ArrayList<>(parseConfigSlots("gui.order-slots"));
        if (slots.isEmpty()) {
            int max = Math.min(45, getInventorySize());
            for (int slot = 0; slot < max; slot++) slots.add(slot);
        }
        return slots;
    }

    private int getControlSlot(String key) {
        return plugin.getConfig().getInt("gui.controls." + key + ".slot", switch (key) {
            case "collection" -> 4;
            case "sort" -> 45;
            case "search" -> 46;
            case "refresh" -> 49;
            case "close" -> 52;
            case "next" -> 53;
            default -> -1;
        });
    }

    private String configString(String path, String fallback) {
        String value = plugin.getConfig().getString(path);
        return value == null ? fallback : value;
    }

    private String configStringWithAliases(String fallback, String... paths) {
        for (String path : paths) {
            String value = plugin.getConfig().getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
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
                .replace("{buyer}", formatBuyerName(order.getBuyerName()))
                .replace("{material}", OrderManager.materialName(order.getMaterial()))
                .replace("{remaining}", String.valueOf(order.getRemaining()))
                .replace("{wanted}", String.valueOf(order.getQuantityWanted()))
                .replace("{filled}", String.valueOf(order.getQuantityFilled()))
                .replace("{wanted_compact}", compactAmount(order.getQuantityWanted()))
                .replace("{filled_compact}", compactAmount(order.getQuantityFilled()))
                .replace("{price_each}", plugin.getCurrencyManager().format(order.getPriceEach()))
                .replace("{price_each_plain}", plugin.getCurrencyManager().formatPlain(order.getPriceEach()))
                .replace("{your_items}", String.valueOf(available))
                .replace("{max_fill_amount}", String.valueOf(Math.min(available, order.getRemaining())))
                .replace("{max_payout}", plugin.getCurrencyManager().format(Math.min(available, order.getRemaining()) * order.getPriceEach()))
                .replace("{max_payout_plain}", plugin.getCurrencyManager().formatPlain(Math.min(available, order.getRemaining()) * order.getPriceEach()))
                .replace("{payout}", plugin.getCurrencyManager().format(payout));
    }

    private String formatBuyerName(String buyerName) {
        if (buyerName == null || buyerName.isBlank()) return "Unknown";
        boolean hasUppercase = buyerName.chars().anyMatch(Character::isUpperCase);
        if (hasUppercase) return buyerName;
        return Character.toUpperCase(buyerName.charAt(0)) + buyerName.substring(1);
    }

    private String compactAmount(long amount) {
        long abs = Math.abs(amount);
        if (abs >= 1_000_000_000L) {
            return trimTrailingZero((double) amount / 1_000_000_000D) + "b";
        }
        if (abs >= 1_000_000L) {
            return trimTrailingZero((double) amount / 1_000_000D) + "m";
        }
        if (abs >= 1_000L) {
            return trimTrailingZero((double) amount / 1_000D) + "k";
        }
        return String.valueOf(amount);
    }

    private String trimTrailingZero(double value) {
        String formatted = String.format(java.util.Locale.ROOT, "%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    private List<String> buildTemplateTooltipLines(ItemStack template) {
        List<String> configured = plugin.getConfig().getStringList("gui.order-item.template-tooltip.lines");
        if (configured.isEmpty()) {
            configured = List.of(
                    "&8&m----------------",
                    "&fItem: &b{template_name}",
                    "{template_lore_lines}",
                    "{template_enchants_lines}",
                    "{template_nbt_lines}",
                    "&8&m----------------"
            );
        }

        ItemMeta meta = template.getItemMeta();
        String rawDisplayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : OrderManager.materialName(template.getType());
        String displayName = rawDisplayName == null || rawDisplayName.isBlank() ? OrderManager.materialName(template.getType()) : rawDisplayName;
        String materialName = OrderManager.materialName(template.getType());
        String customModelData = meta != null && meta.hasCustomModelData()
                ? String.valueOf(meta.getCustomModelData())
                : "None";

        List<String> loreLines = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : List.of();
        List<String> enchantLines = buildEnchantmentLines(meta);
        List<String> nbtLines = buildNbtLines(template);

        List<String> expanded = new ArrayList<>();
        for (String configuredLine : configured) {
            String line = configuredLine == null ? "" : configuredLine;
            String trimmed = line.trim();

            if ("{template_lore_lines}".equalsIgnoreCase(trimmed)) {
                expanded.addAll(loreLines);
                continue;
            }
            if ("{template_enchants_lines}".equalsIgnoreCase(trimmed)) {
                expanded.addAll(enchantLines);
                continue;
            }
            if ("{template_nbt_lines}".equalsIgnoreCase(trimmed)) {
                expanded.addAll(nbtLines);
                continue;
            }

            expanded.add(line
                    .replace("{template_name}", displayName)
                    .replace("{template_material}", materialName)
                    .replace("{template_custom_model_data}", customModelData));
        }

        return expanded;
    }

    private List<String> buildEnchantmentLines(ItemMeta meta) {
        if (meta == null || !meta.hasEnchants()) return List.of();

        return meta.getEnchants().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(enchant -> enchant.getKey().toString())))
                .map(entry -> "&7" + formatEnchantmentName(entry.getKey()) + " " + toRoman(entry.getValue()))
                .collect(Collectors.toList());
    }

    private List<String> buildNbtLines(ItemStack template) {
        Map<String, String> values = new LinkedHashMap<>(ItemMatcher.describeStringPersistentData(template));
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

    private String formatEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey().replace('_', ' ');
        String[] words = key.split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!formatted.isEmpty()) formatted.append(' ');
            formatted.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return formatted.toString();
    }

    private String toRoman(int value) {
        if (value <= 0) return String.valueOf(value);
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] romans = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                result.append(romans[i]);
                remaining -= numbers[i];
            }
        }
        return result.toString();
    }

    private String color(String input) {
        return BuyOrders.color(input);
    }

    private List<String> colorLines(List<String> lines) {
        List<String> colored = new ArrayList<>();
        for (String line : lines) colored.add(color(line));
        return colored;
    }
}
