package com.example.buyorders.gui;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.manager.OrderManager;
import com.example.buyorders.model.AuctionListing;
import com.example.buyorders.util.ItemMatcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class AuctionHouseGUI implements InventoryHolder {

    private static final Pattern SLOT_RANGE = Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$");

    public enum SortMode {
        HIGHEST_PRICE,
        LOWEST_PRICE,
        RECENTLY_LISTED,
        BIDS_ONLY
    }

    private final BuyOrders plugin;
    private final Player player;
    private String searchQuery = "";
    private int page;
    private SortMode sortMode = SortMode.RECENTLY_LISTED;
    private Inventory inventory;

    public AuctionHouseGUI(BuyOrders plugin, Player player) {
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
        List<Integer> listingSlots = getListingSlots();
        int itemsPerPage = listingSlots.size();
        List<AuctionListing> listings = getVisibleListings();
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) itemsPerPage));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        var stats = plugin.getAuctionHouseManager().getPlayerStats(player.getUniqueId());
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("{page}", String.valueOf(page + 1));
        placeholders.put("{total_pages}", String.valueOf(totalPages));
        placeholders.put("{listing_count}", String.valueOf(listings.size()));
        placeholders.put("{claim_count}", String.valueOf(plugin.getAuctionHouseManager().getPendingClaimCount(player.getUniqueId())));
        placeholders.put("{total_listings}", String.valueOf(plugin.getAuctionHouseManager().getListingCount()));
        placeholders.put("{total_value}", plugin.getCurrencyManager().format(plugin.getAuctionHouseManager().getTotalListingValue()));
        placeholders.put("{your_active_listings}", String.valueOf(plugin.getAuctionHouseManager().getActiveListingCount(player.getUniqueId())));
        placeholders.put("{your_active_value}", plugin.getCurrencyManager().format(plugin.getAuctionHouseManager().getActiveListingValue(player.getUniqueId())));
        placeholders.put("{items_sold}", String.valueOf(stats.soldCount()));
        placeholders.put("{items_bought}", String.valueOf(stats.boughtCount()));
        placeholders.put("{earned}", plugin.getCurrencyManager().format(stats.earned()));
        placeholders.put("{spent}", plugin.getCurrencyManager().format(stats.spent()));
        placeholders.put("{sort}", sortLabel());
        placeholders.put("{search}", searchLabel());
        placeholders.put("{highest_price}", sortLine(SortMode.HIGHEST_PRICE));
        placeholders.put("{lowest_price}", sortLine(SortMode.LOWEST_PRICE));
        placeholders.put("{recently_listed}", sortLine(SortMode.RECENTLY_LISTED));
        placeholders.put("{bids_only}", sortLine(SortMode.BIDS_ONLY));

        Inventory inv = Bukkit.createInventory(this, getInventorySize(), color(applyPlaceholders(configString("ah-gui.title", "&6Auction House"), placeholders)));
        this.inventory = inv;

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, listings.size());
        for (int index = start; index < end; index++) {
            inv.setItem(listingSlots.get(index - start), buildListingItem(listings.get(index)));
        }

        if (plugin.getConfig().getBoolean("ah-gui.filler.enabled", true)) {
            ItemStack filler = makeConfiguredItem("ah-gui.filler", Map.of());
            for (int slot : getConfiguredSlots("ah-gui.filler.slots", List.of())) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, filler);
            }
        }

        if (plugin.isGuiFeatureEnabled("auction", "sort")) setControl(inv, "sort", placeholders);
        if (plugin.isGuiFeatureEnabled("auction", "search")) setControl(inv, "search", placeholders);
        if (plugin.isGuiFeatureEnabled("auction", "stats")) setControl(inv, "info", placeholders);
        if (plugin.isGuiFeatureEnabled("auction", "claim")) setControl(inv, "claim", placeholders);
        if (plugin.isGuiFeatureEnabled("auction", "close")) setControl(inv, "close", placeholders);
        if (plugin.isGuiFeatureEnabled("auction", "paging")) setControl(inv, "next", placeholders);

        return inv;
    }

    public AuctionListing getListingAtSlot(int slot) {
        List<Integer> listingSlots = getListingSlots();
        int slotIndex = listingSlots.indexOf(slot);
        if (slotIndex < 0) return null;
        int index = page * listingSlots.size() + slotIndex;
        List<AuctionListing> listings = getVisibleListings();
        return index >= listings.size() ? null : listings.get(index);
    }

    public void previousPage() {
        if (page > 0) page--;
    }

    public void nextPage() {
        int totalPages = Math.max(1, (int) Math.ceil(getVisibleListings().size() / (double) getListingSlots().size()));
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

    public int getSlotSort() { return plugin.isGuiFeatureEnabled("auction", "sort") ? getControlSlot("sort") : -1; }
    public int getSlotSearch() { return plugin.isGuiFeatureEnabled("auction", "search") ? getControlSlot("search") : -1; }
    public int getSlotInfo() { return plugin.isGuiFeatureEnabled("auction", "stats") ? getControlSlot("info") : -1; }
    public int getSlotClaim() { return plugin.isGuiFeatureEnabled("auction", "claim") ? getControlSlot("claim") : -1; }
    public int getSlotClose() { return plugin.isGuiFeatureEnabled("auction", "close") ? getControlSlot("close") : -1; }
    public int getSlotNext() { return plugin.isGuiFeatureEnabled("auction", "paging") ? getControlSlot("next") : -1; }

    private ItemStack buildListingItem(AuctionListing listing) {
        ItemStack item = listing.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
        List<String> configured = plugin.getConfig().getStringList("ah-gui.listing-item.lore");
        if (configured.isEmpty()) {
            configured = List.of(
                    "",
                    "&eSeller: &f{seller}",
                    "&ePrice: &a{price}",
                    "&eID: &f#{id}",
                    "",
                    "&7Left-click to buy",
                    "&7Right-click to cancel your listing"
            );
        }
        Map<String, String> listingPlaceholders = new java.util.HashMap<>();
        listingPlaceholders.put("{seller}", listing.getSellerName());
        listingPlaceholders.put("{price}", plugin.getCurrencyManager().format(listing.getPrice()));
        listingPlaceholders.put("{price_plain}", plugin.getCurrencyManager().formatPlain(listing.getPrice()));
        listingPlaceholders.put("{starting_price}", plugin.getCurrencyManager().format(listing.getStartingPrice()));
        listingPlaceholders.put("{current_bid}", plugin.getCurrencyManager().format(listing.getCurrentBid()));
        listingPlaceholders.put("{display_price}", plugin.getCurrencyManager().format(listing.getDisplayPrice()));
        listingPlaceholders.put("{listing_type}", listing.isAuction() ? "Auction" : "Buy Now");
        listingPlaceholders.put("{listing_type_short}", listing.isAuction() ? "BID" : "BUY");
        listingPlaceholders.put("{bid_count}", String.valueOf(listing.getBidCount()));
        listingPlaceholders.put("{highest_bidder}", listing.getHighestBidderName() == null || listing.getHighestBidderName().isBlank() ? "None" : listing.getHighestBidderName());
        listingPlaceholders.put("{time_left}", formatTimeLeft(listing));
        listingPlaceholders.put("{id}", listing.getShortId());
        listingPlaceholders.put("{material}", OrderManager.materialName(item.getType()));

        lore.addAll(colorLines(applyPlaceholders(configured, listingPlaceholders)));

        if (plugin.getConfig().getBoolean("ah-gui.item-tooltip.enabled", true)) {
            List<String> nbtLines = buildNbtLines(item);
            if (!nbtLines.isEmpty()) {
                if (plugin.getConfig().getBoolean("ah-gui.item-tooltip.separator", true) && !lore.isEmpty()) {
                    lore.add("");
                }
                lore.addAll(nbtLines);
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<AuctionListing> getVisibleListings() {
        List<AuctionListing> listings = new ArrayList<>(plugin.getAuctionHouseManager().getListings());

        String normalizedSearch = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (!normalizedSearch.isEmpty()) {
            listings.removeIf(listing -> !matchesSearch(listing, normalizedSearch));
        }

        if (sortMode == SortMode.BIDS_ONLY) {
            listings.removeIf(listing -> !listing.isAuction());
            listings.sort(Comparator.comparingLong(AuctionListing::getEndAt));
            return listings;
        }

        switch (sortMode) {
            case HIGHEST_PRICE -> listings.sort(Comparator.comparingDouble(AuctionListing::getDisplayPrice).reversed());
            case LOWEST_PRICE -> listings.sort(Comparator.comparingDouble(AuctionListing::getDisplayPrice));
            case RECENTLY_LISTED -> listings.sort(Comparator.comparingLong(AuctionListing::getCreatedAt).reversed());
            case BIDS_ONLY -> {
                // handled above
            }
        }

        return listings;
    }

    private boolean matchesSearch(AuctionListing listing, String normalizedSearch) {
        if (normalizedSearch.equals("bid") || normalizedSearch.equals("bids")
                || normalizedSearch.equals("auction") || normalizedSearch.equals("type:bid")
                || normalizedSearch.equals("type:auction")) {
            return listing.isAuction();
        }

        if (normalizedSearch.equals("buy") || normalizedSearch.equals("buynow")
                || normalizedSearch.equals("type:buy") || normalizedSearch.equals("type:buynow")) {
            return !listing.isAuction();
        }

        if (normalizedSearch.startsWith("id:")) {
            String shortIdQuery = normalizedSearch.substring(3).trim();
            return !shortIdQuery.isEmpty() && listing.getShortId().toLowerCase(Locale.ROOT).contains(shortIdQuery);
        }

        if (listing.getSellerName().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                || listing.getShortId().toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
            return true;
        }

        ItemStack item = listing.getItem();
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

    private void setControl(Inventory inv, String key, Map<String, String> placeholders) {
        int slot = getControlSlot(key);
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, makeConfiguredItem("ah-gui.controls." + key, placeholders));
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
            case "ah-gui.controls.previous", "ah-gui.controls.next" -> "ARROW";
            case "ah-gui.controls.sort" -> "ANVIL";
            case "ah-gui.controls.search" -> "SPYGLASS";
            case "ah-gui.controls.info" -> "ENCHANTED_BOOK";
            case "ah-gui.controls.claim" -> "CHEST";
            case "ah-gui.controls.close" -> "BARRIER";
            case "ah-gui.filler" -> "BLACK_STAINED_GLASS_PANE";
            default -> "STONE";
        };
    }

    private String defaultControlName(String path) {
        return switch (path) {
            case "ah-gui.controls.previous" -> "&bPrevious Page";
            case "ah-gui.controls.sort" -> "&bFilter: &f{sort}";
            case "ah-gui.controls.search" -> "&bSearch: &f{search}";
            case "ah-gui.controls.info" -> "&bStatistics";
            case "ah-gui.controls.claim" -> "&bMy Claims: &f{claim_count}";
            case "ah-gui.controls.close" -> "&bClose";
            case "ah-gui.controls.next" -> "&bChange Page";
            default -> " ";
        };
    }

    private List<String> defaultControlLore(String path) {
        return switch (path) {
            case "ah-gui.controls.sort" -> List.of("&7Click to change");
            case "ah-gui.controls.search" -> List.of("&7Left-click to type", "&7Right-click to clear");
                case "ah-gui.controls.info" -> List.of(
                    "&7Global: &f{total_listings} listing(s)",
                    "&7Global Value: &f{total_value}",
                    "",
                    "&7Your Active: &f{your_active_listings}",
                    "&7Your Active Value: &f{your_active_value}",
                    "&7Sold: &f{items_sold}  &7Bought: &f{items_bought}",
                    "&7Earned: &f{earned}",
                    "&7Spent: &f{spent}",
                    "",
                    "&7Use &f/ah sell <price> [amount]"
                );
            case "ah-gui.controls.claim" -> List.of("&7Click to claim pending purchases");
            case "ah-gui.controls.close" -> List.of("&7Click to close");
            default -> List.of();
        };
    }

    private String sortLabel() {
        return switch (sortMode) {
            case HIGHEST_PRICE -> configString("ah-gui.sort-labels.highest_price", "Highest Price");
            case LOWEST_PRICE -> configString("ah-gui.sort-labels.lowest_price", "Lowest Price");
            case RECENTLY_LISTED -> configString("ah-gui.sort-labels.recently_listed", "Recently Listed");
            case BIDS_ONLY -> configString("ah-gui.sort-labels.bids_only", "Bids Only");
        };
    }

    private String sortLine(SortMode mode) {
        String prefix = mode == sortMode ? "&f• " : "&8• ";
        String color = mode == sortMode ? "&f" : "&7";

        String label = switch (mode) {
            case HIGHEST_PRICE -> configString("ah-gui.sort-labels.highest_price", "Highest Price");
            case LOWEST_PRICE -> configString("ah-gui.sort-labels.lowest_price", "Lowest Price");
            case RECENTLY_LISTED -> configString("ah-gui.sort-labels.recently_listed", "Recently Listed");
            case BIDS_ONLY -> configString("ah-gui.sort-labels.bids_only", "Bids Only");
        };

        return prefix + color + label;
    }

    private String searchLabel() {
        if (searchQuery.trim().isEmpty()) {
            return configString("ah-gui.search-empty", "None");
        }
        return searchQuery;
    }

    private int getInventorySize() {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("ah-gui.rows", 6)));
        return rows * 9;
    }

    private List<Integer> getListingSlots() {
        List<Integer> slots = getConfiguredSlots("ah-gui.listing-slots", List.of());
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

    private List<Integer> getConfiguredSlots(String path, List<Integer> fallback) {
        List<?> raw = plugin.getConfig().getList(path);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>(fallback);
        }

        List<Integer> slots = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof Number number) {
                slots.add(number.intValue());
                continue;
            }

            String text = String.valueOf(entry).trim();
            Matcher matcher = SLOT_RANGE.matcher(text);
            if (matcher.matches()) {
                int start = Integer.parseInt(matcher.group(1));
                int end = Integer.parseInt(matcher.group(2));
                int step = start <= end ? 1 : -1;
                for (int slot = start; slot != end + step; slot += step) slots.add(slot);
                continue;
            }

            try {
                slots.add(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
            }
        }

        return slots.stream().distinct().toList();
    }

    private int getControlSlot(String key) {
        return plugin.getConfig().getInt("ah-gui.controls." + key + ".slot", switch (key) {
            case "previous" -> -1;
            case "sort" -> 45;
            case "search" -> 46;
            case "info" -> 4;
            case "claim" -> 49;
            case "close" -> 52;
            case "next" -> 53;
            default -> -1;
        });
    }

    private String configString(String path, String fallback) {
        String value = plugin.getConfig().getString(path);
        return value == null ? fallback : value;
    }

    private String applyPlaceholders(String line, Map<String, String> placeholders) {
        String replaced = line.replace("{store_name}", plugin.getConfig().getString("store-name", "&6&lBuy Orders"));
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
        for (Map.Entry<String, String> entry : values.entrySet()) {
            lines.add(color("&8• &7" + entry.getKey() + ": &f" + entry.getValue()));
        }
        return lines;
    }

    private String formatTimeLeft(AuctionListing listing) {
        if (!listing.isAuction()) return "-";

        long endAt = listing.getEndAt();
        if (endAt <= 0L) return "-";

        long remainingMs = endAt - System.currentTimeMillis();
        if (remainingMs <= 0L) return "Ended";

        long totalSeconds = remainingMs / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
