package com.example.buyorders.gui;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.manager.OrderManager;
import com.example.buyorders.util.ItemMatcher;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("deprecation")
public class CollectionGUI implements InventoryHolder {

    public enum SortMode {
        HIGHEST_AMOUNT, LOWEST_AMOUNT, RECENTLY_ADDED
    }

    private record PendingEntry(int originalIndex, ItemStack item) {
    }

    private final BuyOrders plugin;
    private final Player player;
    private String searchQuery = "";
    private int page;
    private SortMode sortMode = SortMode.RECENTLY_ADDED;
    private Inventory inventory;

    public CollectionGUI(BuyOrders plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(buildInventory());
    }

    public Inventory buildInventory() {
        List<Integer> itemSlots = getItemSlots();
        int itemsPerPage = itemSlots.size();
        List<PendingEntry> pending = getVisiblePending();
        int totalPages = Math.max(1, (int) Math.ceil(pending.size() / (double) itemsPerPage));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Map<String, String> placeholders = Map.of(
            "{page}", String.valueOf(page + 1),
            "{total_pages}", String.valueOf(totalPages),
            "{pending_count}", String.valueOf(pending.size()),
            "{sort}", sortLabel(),
            "{search}", searchLabel(),
            "{highest_amount}", sortLine(SortMode.HIGHEST_AMOUNT),
            "{lowest_amount}", sortLine(SortMode.LOWEST_AMOUNT),
            "{recently_added}", sortLine(SortMode.RECENTLY_ADDED)
        );

        placeholders = new java.util.HashMap<>(placeholders);
        placeholders.put("{newest}", sortLine(SortMode.RECENTLY_ADDED));
        placeholders.put("{Newest}", sortLine(SortMode.RECENTLY_ADDED));
        placeholders.put("{oldest}", sortLine(SortMode.RECENTLY_ADDED));
        placeholders.put("{Oldest}", sortLine(SortMode.RECENTLY_ADDED));

        // Cross-GUI aliases to prevent raw placeholder text when configs are mixed.
        placeholders.put("{highest_price}", sortLine(SortMode.HIGHEST_AMOUNT));
        placeholders.put("{lowest_price}", sortLine(SortMode.LOWEST_AMOUNT));
        placeholders.put("{recently_listed}", sortLine(SortMode.RECENTLY_ADDED));
        placeholders.put("{most_per_item}", sortLine(SortMode.HIGHEST_AMOUNT));
        placeholders.put("{most_paid}", sortLine(SortMode.LOWEST_AMOUNT));

        Inventory inv = Bukkit.createInventory(this, getInventorySize(), color(applyPlaceholders(configString("collection-gui.title", "&0Collection (&f{page}&7/&f{total_pages}&0)"), placeholders)));
        this.inventory = inv;

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, pending.size());
        for (int index = start; index < end; index++) {
            inv.setItem(itemSlots.get(index - start), buildPendingItem(pending.get(index).item()));
        }

        if (plugin.getConfig().getBoolean("collection-gui.filler.enabled", true)) {
            ItemStack filler = makeConfiguredItem("collection-gui.filler", Map.of());
            for (int slot : parseConfigSlots("collection-gui.filler.slots")) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, filler);
            }
        }

        if (plugin.isGuiFeatureEnabled("collection", "back")) setControl(inv, "back", placeholders);
        if (plugin.isGuiFeatureEnabled("collection", "info")) setControl(inv, "info", placeholders);
        if (plugin.isGuiFeatureEnabled("collection", "claim_all")) setControl(inv, "claim-all", placeholders);
        if (totalPages > 1) {
            if (plugin.isGuiFeatureEnabled("collection", "paging")) setControl(inv, "next", placeholders);
        }

        return inv;
    }

    public boolean claimSlot(int slot) {
        int index = getPendingIndex(slot);
        if (index < 0) return false;

        List<PendingEntry> pending = getVisiblePending();
        if (index >= pending.size()) return false;

        PendingEntry entry = pending.get(index);
        ItemStack item = entry.item();
        if (!hasRoomFor(player, item)) {
            player.sendMessage(plugin.msg("collection-full"));
            return true;
        }

        if (!plugin.getOrderManager().removePendingDelivery(player.getUniqueId(), entry.originalIndex())) {
            player.sendMessage(plugin.msg("order-save-failed"));
            return true;
        }

        player.getInventory().addItem(item.clone());
        player.sendMessage(plugin.msg("collection-claimed"));
        player.openInventory(buildInventory());
        return true;
    }

    public void claimAll() {
        int claimed = 0;
        List<PendingEntry> visible = getVisiblePending();
        boolean hadPending = !visible.isEmpty();
        List<PendingEntry> claimable = new ArrayList<>();

        for (PendingEntry entry : visible) {
            if (!hasRoomFor(player, entry.item())) break;
            claimable.add(entry);
        }

        List<PendingEntry> removals = new ArrayList<>(claimable);
        removals.sort(Comparator.comparingInt(PendingEntry::originalIndex).reversed());

        for (PendingEntry entry : removals) {
            if (!plugin.getOrderManager().removePendingDelivery(player.getUniqueId(), entry.originalIndex())) {
                player.sendMessage(plugin.msg("order-save-failed"));
                break;
            }

            player.getInventory().addItem(entry.item().clone());
            claimed++;
        }

        if (claimed > 0) {
            player.sendMessage(plugin.msg("collection-claimed-all", "{amount}", String.valueOf(claimed)));
        } else if (!hadPending) {
            player.sendMessage(plugin.msg("collection-empty"));
        } else {
            player.sendMessage(plugin.msg("collection-full"));
        }
        player.openInventory(buildInventory());
    }

    public void previousPage() {
        if (page > 0) page--;
    }

    public void nextPage() {
        int totalPages = Math.max(1, (int) Math.ceil(getVisiblePending().size() / (double) getItemSlots().size()));
        if (page < totalPages - 1) page++;
    }

    public void cycleSortMode() {
        SortMode[] modes = SortMode.values();
        sortMode = modes[(sortMode.ordinal() + 1) % modes.length];
        page = 0;
    }

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

    public int getSlotPrevious() { return getControlSlot("previous"); }
    public int getSlotSort() { return -1; }
    public int getSlotSearch() { return -1; }
    public int getSlotBack() { return plugin.isGuiFeatureEnabled("collection", "back") ? getControlSlot("back") : -1; }
    public int getSlotInfo() { return plugin.isGuiFeatureEnabled("collection", "info") ? getControlSlot("info") : -1; }
    public int getSlotClaimAll() { return plugin.isGuiFeatureEnabled("collection", "claim_all") ? getControlSlot("claim-all") : -1; }
    public int getSlotNext() { return plugin.isGuiFeatureEnabled("collection", "paging") ? getControlSlot("next") : -1; }

    private int getPendingIndex(int slot) {
        List<Integer> itemSlots = getItemSlots();
        int slotIndex = itemSlots.indexOf(slot);
        if (slotIndex < 0) return -1;
        return page * itemSlots.size() + slotIndex;
    }

    private List<PendingEntry> getVisiblePending() {
        List<ItemStack> pending = plugin.getOrderManager().getPendingDeliveries(player.getUniqueId());
        List<PendingEntry> visible = new ArrayList<>();

        String normalizedSearch = searchQuery.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < pending.size(); i++) {
            ItemStack item = pending.get(i);
            if (item == null || item.getType().isAir()) continue;
            if (!normalizedSearch.isEmpty() && !matchesSearch(item, normalizedSearch)) continue;
            visible.add(new PendingEntry(i, item));
        }

        switch (sortMode) {
            case HIGHEST_AMOUNT -> visible.sort(Comparator.comparingInt((PendingEntry entry) -> entry.item().getAmount()).reversed());
            case LOWEST_AMOUNT -> visible.sort(Comparator.comparingInt(entry -> entry.item().getAmount()));
            case RECENTLY_ADDED -> visible.sort(Comparator.comparingInt(PendingEntry::originalIndex).reversed());
        }

        return visible;
    }

    private boolean matchesSearch(ItemStack item, String normalizedSearch) {
        if (OrderManager.materialName(item.getType()).toLowerCase(Locale.ROOT).contains(normalizedSearch)
                || item.getType().name().toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        if (meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
            return true;
        }

        if (!meta.hasLore() || meta.getLore() == null) return false;
        for (String line : meta.getLore()) {
            if (ChatColor.stripColor(line).toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
                return true;
            }
        }
        return false;
    }

    private String searchLabel() {
        if (searchQuery.trim().isEmpty()) {
            return configString("collection-gui.search-empty", "None");
        }
        return searchQuery;
    }

    private String sortLabel() {
        return sortLabel(sortMode);
    }

    private String sortLabel(SortMode mode) {
        return switch (mode) {
            case HIGHEST_AMOUNT -> configString("collection-gui.sort-labels.highest_amount", "Highest Amount");
            case LOWEST_AMOUNT -> configString("collection-gui.sort-labels.lowest_amount", "Lowest Amount");
            case RECENTLY_ADDED -> configString("collection-gui.sort-labels.recently_added", "Recently Added");
        };
    }

    private String sortLine(SortMode mode) {
        String prefix = mode == sortMode ? "&f• " : "&8• ";
        String color = mode == sortMode ? "&f" : "&7";
        return prefix + color + sortLabel(mode);
    }

    private ItemStack buildPendingItem(ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        List<String> configuredLore = plugin.getConfig().getStringList("collection-gui.item-lore");
        if (configuredLore.isEmpty()) configuredLore = List.of("", "&7Click to collect");
        lore.addAll(colorLines(applyPlaceholders(configuredLore, Map.of("{amount}", String.valueOf(item.getAmount())))));

        if (plugin.getConfig().getBoolean("collection-gui.item-tooltip.enabled", true)) {
            List<String> nbtLines = buildNbtLines(item);
            if (!nbtLines.isEmpty()) {
                if (plugin.getConfig().getBoolean("collection-gui.item-tooltip.separator", true) && !lore.isEmpty()) {
                    lore.add("");
                }
                lore.addAll(nbtLines);
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void setControl(Inventory inv, String key, Map<String, String> placeholders) {
        int slot = getControlSlot(key);
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, makeConfiguredItem("collection-gui.controls." + key, placeholders));
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

        if (plugin.getConfig().isSet(path + ".custom-model-data")) {
            meta.setCustomModelData(plugin.getConfig().getInt(path + ".custom-model-data"));
        }

        item.setItemMeta(meta);
        return item;
    }

    private static boolean hasRoomFor(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return true;

        int remaining = item.getAmount();
        PlayerInventory inventory = player.getInventory();
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) {
                remaining -= item.getMaxStackSize();
            } else if (current.isSimilar(item)) {
                remaining -= current.getMaxStackSize() - current.getAmount();
            }

            if (remaining <= 0) return true;
        }
        return false;
    }

    private int getInventorySize() {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("collection-gui.rows", 6)));
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

    private List<Integer> getItemSlots() {
        List<Integer> slots = new ArrayList<>(parseConfigSlots("collection-gui.item-slots"));
        if (slots.isEmpty()) {
            int max = Math.min(45, getInventorySize());
            for (int slot = 0; slot < max; slot++) slots.add(slot);
        }
        return slots;
    }

    private int getControlSlot(String key) {
        return plugin.getConfig().getInt("collection-gui.controls." + key + ".slot", switch (key) {
            case "sort" -> 45;
            case "search" -> 46;
            case "back" -> 48;
            case "info" -> 49;
            case "claim-all" -> 50;
            case "next" -> 53;
            default -> -1;
        });
    }

    private String defaultItemMaterial(String path) {
        return switch (path) {
            case "collection-gui.controls.next" -> "ARROW";
            case "collection-gui.controls.sort" -> "ANVIL";
            case "collection-gui.controls.search" -> "SPYGLASS";
            case "collection-gui.controls.back" -> "BARRIER";
            case "collection-gui.controls.info" -> "PAPER";
            case "collection-gui.controls.claim-all" -> "EMERALD_BLOCK";
            case "collection-gui.filler" -> "BLACK_STAINED_GLASS_PANE";
            default -> "STONE";
        };
    }

    private String defaultItemName(String path) {
        return switch (path) {
            case "collection-gui.controls.sort" -> "&bSort: &f{sort}";
            case "collection-gui.controls.search" -> "&bSearch: &f{search}";
            case "collection-gui.controls.back" -> "&bBack to Orders";
            case "collection-gui.controls.info" -> "&fCollection &7{page}&f/&7{total_pages}";
            case "collection-gui.controls.claim-all" -> "&bClaim All";
            case "collection-gui.controls.next" -> "&bChange Page";
            default -> " ";
        };
    }

    private List<String> defaultItemLore(String path) {
        return switch (path) {
            case "collection-gui.controls.sort" -> List.of("&7Click to change", "{highest_amount}", "{lowest_amount}", "{recently_added}");
            case "collection-gui.controls.search" -> List.of("&7Left-click to search", "&7Right-click to clear");
            case "collection-gui.controls.info" -> List.of("&7{pending_count} item stack(s) waiting");
            case "collection-gui.controls.claim-all" -> List.of("&7Claim everything that fits");
            case "collection-gui.controls.next" -> List.of("&7Left-click \u00bb Next Page", "&7Right-click \u00bb Previous Page");
            default -> List.of();
        };
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
        lines.add(color(" "));
        lines.add(color("&8&m--------------------------"));
        lines.add(color(" &bNBT Data"));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            lines.add(color(" &8• &7" + entry.getKey() + " &8» &f" + entry.getValue()));
        }
        lines.add(color("&8&m--------------------------"));
        return lines;
    }
}
