package com.example.buyorders.manager;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.model.AuctionListing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class AuctionHouseManager {

    public record PlayerStats(int soldCount, int boughtCount, double earned, double spent) {
    }

    public record PurchaseResult(String error, boolean deliveredToInventory, ItemStack purchasedItem) {
        public static PurchaseResult error(String error) {
            return new PurchaseResult(error, false, null);
        }

        public static PurchaseResult success(boolean deliveredToInventory, ItemStack purchasedItem) {
            return new PurchaseResult(null, deliveredToInventory, purchasedItem == null ? null : purchasedItem.clone());
        }
    }

    public record CancelResult(String error, boolean returnedToInventory, ItemStack cancelledItem) {
        public static CancelResult error(String error) {
            return new CancelResult(error, false, null);
        }

        public static CancelResult success(boolean returnedToInventory, ItemStack cancelledItem) {
            return new CancelResult(null, returnedToInventory, cancelledItem == null ? null : cancelledItem.clone());
        }
    }

    public record BidResult(String error, double bidAmount, boolean extended) {
        public static BidResult error(String error) {
            return new BidResult(error, 0.0D, false);
        }

        public static BidResult success(double bidAmount, boolean extended) {
            return new BidResult(null, bidAmount, extended);
        }
    }

    private final BuyOrders plugin;
    private final File legacyDataFile;
    private final File databaseFile;
    private Connection connection;

    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingClaims = new HashMap<>();
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();

    public AuctionHouseManager(BuyOrders plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        this.legacyDataFile = new File(plugin.getDataFolder(), "auctions.yml");
        this.databaseFile = new File(dataFolder, "auctions");
    }

    public synchronized void load() {
        listings.clear();
        pendingClaims.clear();
        playerStats.clear();

        try {
            initDatabase();
            loadFromDatabase();

            if (!isLegacyYamlMigrated() && legacyDataFile.exists() && isDatabaseEmpty()) {
                loadFromLegacyYaml();
                if (save()) {
                    setMetadata("legacy_yaml_migrated", "true");
                    plugin.getLogger().info("Migrated auctions.yml into H2 database.");
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed loading auctions database", ex);
        }
    }

    private void initDatabase() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        if (connection != null && !connection.isClosed()) return;

        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("H2 driver is not available.", ex);
        }
        String url = "jdbc:h2:" + databaseFile.getAbsolutePath().replace("\\", "/") + ";DATABASE_TO_UPPER=false";
        connection = DriverManager.getConnection(url);

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS listings (
                        id VARCHAR(36) PRIMARY KEY,
                        seller_uuid VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(64) NOT NULL,
                        item BLOB NOT NULL,
                        price DOUBLE NOT NULL,
                        created_at BIGINT NOT NULL,
                        listing_type VARCHAR(16) NOT NULL DEFAULT 'BUY_NOW',
                        starting_price DOUBLE NOT NULL DEFAULT 0,
                        current_bid DOUBLE NOT NULL DEFAULT 0,
                        highest_bidder_uuid VARCHAR(36),
                        highest_bidder_name VARCHAR(64),
                        end_at BIGINT NOT NULL DEFAULT 0,
                        bid_count INT NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_claims (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        sort_order INT NOT NULL,
                        item BLOB NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        sold_count INT NOT NULL,
                        bought_count INT NOT NULL,
                        earned DOUBLE NOT NULL,
                        spent DOUBLE NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS metadata (
                        meta_key VARCHAR(64) PRIMARY KEY,
                        meta_value VARCHAR(255) NOT NULL
                    )
                    """);
            statement.executeUpdate("ALTER TABLE listings ADD COLUMN IF NOT EXISTS listing_type VARCHAR(16) NOT NULL DEFAULT 'BUY_NOW'");
            statement.executeUpdate("ALTER TABLE listings ADD COLUMN IF NOT EXISTS starting_price DOUBLE NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE listings ADD COLUMN IF NOT EXISTS current_bid DOUBLE NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE listings ADD COLUMN IF NOT EXISTS highest_bidder_uuid VARCHAR(36)");
            statement.executeUpdate("ALTER TABLE listings ADD COLUMN IF NOT EXISTS highest_bidder_name VARCHAR(64)");
            statement.executeUpdate("ALTER TABLE listings ADD COLUMN IF NOT EXISTS end_at BIGINT NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE listings ADD COLUMN IF NOT EXISTS bid_count INT NOT NULL DEFAULT 0");
        }
    }

    private void loadFromDatabase() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id, seller_uuid, seller_name, item, price, created_at,
                   listing_type, starting_price, current_bid,
                   highest_bidder_uuid, highest_bidder_name, end_at, bid_count
                FROM listings
                ORDER BY created_at ASC
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                try {
                    UUID id = UUID.fromString(result.getString("id"));
                    UUID sellerUuid = UUID.fromString(result.getString("seller_uuid"));
                    String sellerName = result.getString("seller_name");
                    ItemStack item = deserializeItem(result.getBytes("item"));
                    double price = result.getDouble("price");
                    long createdAt = result.getLong("created_at");
                    String typeText = result.getString("listing_type");
                    AuctionListing.ListingType type;
                    try {
                        type = AuctionListing.ListingType.valueOf(typeText == null ? "BUY_NOW" : typeText);
                    } catch (IllegalArgumentException ignored) {
                        type = AuctionListing.ListingType.BUY_NOW;
                    }
                    double startingPrice = result.getDouble("starting_price");
                    double currentBid = result.getDouble("current_bid");
                    String highestBidderUuidRaw = result.getString("highest_bidder_uuid");
                    UUID highestBidderUuid = (highestBidderUuidRaw == null || highestBidderUuidRaw.isBlank()) ? null : UUID.fromString(highestBidderUuidRaw);
                    String highestBidderName = result.getString("highest_bidder_name");
                    long endAt = result.getLong("end_at");
                    int bidCount = result.getInt("bid_count");

                    if (item == null || item.getType().isAir()) continue;
                    if (type == AuctionListing.ListingType.BUY_NOW && price <= 0.0D) continue;
                    if (type == AuctionListing.ListingType.AUCTION && startingPrice <= 0.0D) continue;

                    listings.put(id, new AuctionListing(
                            id,
                            sellerUuid,
                            sellerName,
                            item,
                            price,
                            createdAt,
                            type,
                            startingPrice,
                            currentBid,
                            highestBidderUuid,
                            highestBidderName,
                            endAt,
                            bidCount
                    ));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load AH listing row", ex);
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, item
                FROM pending_claims
                ORDER BY player_uuid ASC, sort_order ASC, id ASC
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                try {
                    UUID uuid = UUID.fromString(result.getString("player_uuid"));
                    ItemStack item = deserializeItem(result.getBytes("item"));
                    if (item == null || item.getType().isAir()) continue;
                    pendingClaims.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(item);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load AH pending claim row", ex);
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, sold_count, bought_count, earned, spent
                FROM player_stats
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                try {
                    UUID uuid = UUID.fromString(result.getString("player_uuid"));
                    playerStats.put(uuid, new PlayerStats(
                            result.getInt("sold_count"),
                            result.getInt("bought_count"),
                            result.getDouble("earned"),
                            result.getDouble("spent")
                    ));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load AH stats row", ex);
                }
            }
        }
    }

    private boolean isDatabaseEmpty() throws SQLException {
        return countRows("listings") == 0 && countRows("pending_claims") == 0 && countRows("player_stats") == 0;
    }

    private int countRows(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private boolean isLegacyYamlMigrated() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT meta_value FROM metadata WHERE meta_key = ?")) {
            statement.setString(1, "legacy_yaml_migrated");
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && Boolean.parseBoolean(result.getString("meta_value"));
            }
        }
    }

    private void setMetadata(String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO metadata (meta_key, meta_value) KEY(meta_key) VALUES (?, ?)
                """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void loadFromLegacyYaml() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(legacyDataFile);

        if (config.isConfigurationSection("listings")) {
            for (String key : config.getConfigurationSection("listings").getKeys(false)) {
                String path = "listings." + key + ".";
                try {
                    UUID id = UUID.fromString(config.getString(path + "id", key));
                    UUID sellerUuid = UUID.fromString(config.getString(path + "seller-uuid"));
                    String sellerName = config.getString(path + "seller-name", "Unknown");
                    ItemStack item = config.getItemStack(path + "item");
                    double price = config.getDouble(path + "price", 0.0D);
                    long createdAt = config.getLong(path + "created-at", System.currentTimeMillis());
                    if (item == null || item.getType().isAir() || price <= 0.0D) continue;

                    listings.put(id, new AuctionListing(id, sellerUuid, sellerName, item, price, createdAt));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load legacy AH listing " + key, ex);
                }
            }
        }

        if (config.isConfigurationSection("pending-claims")) {
            for (String key : config.getConfigurationSection("pending-claims").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<ItemStack> claims = new ArrayList<>();
                    List<?> raw = config.getList("pending-claims." + key, List.of());
                    for (Object value : raw) {
                        if (value instanceof ItemStack item && !item.getType().isAir()) {
                            claims.add(item.clone());
                        }
                    }
                    if (!claims.isEmpty()) pendingClaims.put(uuid, claims);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load legacy AH pending claims " + key, ex);
                }
            }
        }

        if (config.isConfigurationSection("stats.players")) {
            for (String key : config.getConfigurationSection("stats.players").getKeys(false)) {
                String path = "stats.players." + key + ".";
                try {
                    UUID uuid = UUID.fromString(key);
                    playerStats.put(uuid, new PlayerStats(
                            config.getInt(path + "sold-count", 0),
                            config.getInt(path + "bought-count", 0),
                            config.getDouble(path + "earned", 0.0D),
                            config.getDouble(path + "spent", 0.0D)
                    ));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load legacy AH stats " + key, ex);
                }
            }
        }
    }

    public synchronized boolean save() {
        try {
            initDatabase();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM listings");
                statement.executeUpdate("DELETE FROM pending_claims");
                statement.executeUpdate("DELETE FROM player_stats");
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO listings (
                        id, seller_uuid, seller_name, item, price, created_at,
                        listing_type, starting_price, current_bid,
                        highest_bidder_uuid, highest_bidder_name, end_at, bid_count
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (AuctionListing listing : listings.values()) {
                    statement.setString(1, listing.getId().toString());
                    statement.setString(2, listing.getSellerUuid().toString());
                    statement.setString(3, listing.getSellerName());
                    statement.setBytes(4, serializeItem(listing.getItem()));
                    statement.setDouble(5, listing.getPrice());
                    statement.setLong(6, listing.getCreatedAt());
                    statement.setString(7, listing.getListingType().name());
                    statement.setDouble(8, listing.getStartingPrice());
                    statement.setDouble(9, listing.getCurrentBid());
                    statement.setString(10, listing.getHighestBidderUuid() == null ? null : listing.getHighestBidderUuid().toString());
                    statement.setString(11, listing.getHighestBidderName());
                    statement.setLong(12, listing.getEndAt());
                    statement.setInt(13, listing.getBidCount());
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pending_claims (player_uuid, sort_order, item)
                    VALUES (?, ?, ?)
                    """)) {
                for (Map.Entry<UUID, List<ItemStack>> entry : pendingClaims.entrySet()) {
                    int index = 0;
                    for (ItemStack item : entry.getValue()) {
                        if (item == null || item.getType().isAir()) continue;
                        statement.setString(1, entry.getKey().toString());
                        statement.setInt(2, index++);
                        statement.setBytes(3, serializeItem(item));
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_stats (
                        player_uuid, sold_count, bought_count, earned, spent
                    ) VALUES (?, ?, ?, ?, ?)
                    """)) {
                for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
                    PlayerStats stats = entry.getValue();
                    statement.setString(1, entry.getKey().toString());
                    statement.setInt(2, stats.soldCount());
                    statement.setInt(3, stats.boughtCount());
                    statement.setDouble(4, stats.earned());
                    statement.setDouble(5, stats.spent());
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            connection.commit();
            connection.setAutoCommit(previousAutoCommit);
            return true;
        } catch (Exception ex) {
            rollbackQuietly();
            plugin.getLogger().log(Level.SEVERE, "Failed saving auctions database", ex);
            return false;
        }
    }

    private void rollbackQuietly() {
        if (connection == null) return;
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    public synchronized List<AuctionListing> getListings() {
        return listings.values().stream()
                .sorted(Comparator.comparingLong(AuctionListing::getCreatedAt).reversed())
                .toList();
    }

    public synchronized int getListingCount() {
        return listings.size();
    }

    public synchronized int getPendingClaimCount(UUID player) {
        return pendingClaims.getOrDefault(player, List.of()).size();
    }

    public synchronized int getActiveListingCount(UUID player) {
        int count = 0;
        for (AuctionListing listing : listings.values()) {
            if (listing.getSellerUuid().equals(player)) count++;
        }
        return count;
    }

    public synchronized double getActiveListingValue(UUID player) {
        double value = 0.0D;
        for (AuctionListing listing : listings.values()) {
            if (listing.getSellerUuid().equals(player)) {
                value += listing.getPrice();
            }
        }
        return value;
    }

    public synchronized double getTotalListingValue() {
        double total = 0.0D;
        for (AuctionListing listing : listings.values()) {
            total += listing.getPrice();
        }
        return total;
    }

    public synchronized PlayerStats getPlayerStats(UUID player) {
        return playerStats.getOrDefault(player, new PlayerStats(0, 0, 0.0D, 0.0D));
    }

    public synchronized List<ItemStack> getPendingClaims(UUID player) {
        List<ItemStack> claims = pendingClaims.getOrDefault(player, List.of());
        List<ItemStack> clones = new ArrayList<>(claims.size());
        for (ItemStack claim : claims) clones.add(claim.clone());
        return clones;
    }

    public synchronized String createListing(Player seller, ItemStack source, int amount, double price) {
        if (source == null || source.getType().isAir()) {
            return "&cHold the item you want to list in your main hand.";
        }
        if (amount <= 0 || amount > source.getAmount()) {
            return "&cInvalid amount. Use up to the amount in your hand.";
        }
        if (price <= 0.0D) {
            return "&cPrice must be greater than 0.";
        }

        double minPrice = plugin.getConfig().getDouble("ah.min-price", 0.01D);
        double maxPrice = plugin.getConfig().getDouble("ah.max-price", -1.0D);
        if (price < minPrice) {
            return "&cPrice is below minimum (&e" + plugin.getCurrencyManager().format(minPrice) + "&c).";
        }
        if (maxPrice > 0 && price > maxPrice) {
            return "&cPrice is above maximum (&e" + plugin.getCurrencyManager().format(maxPrice) + "&c).";
        }

        if (plugin.isItemBlacklisted(source)) {
            return plugin.msg("item-blacklisted");
        }

        ItemStack listingItem = source.clone();
        listingItem.setAmount(amount);

        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != source.getType() || hand.getAmount() < amount) {
            return "&cYour main hand item changed. Try again.";
        }

        hand.setAmount(hand.getAmount() - amount);
        if (hand.getAmount() <= 0) {
            seller.getInventory().setItemInMainHand(null);
        } else {
            seller.getInventory().setItemInMainHand(hand);
        }
        seller.updateInventory();

        AuctionListing listing = new AuctionListing(
                UUID.randomUUID(),
                seller.getUniqueId(),
                seller.getName(),
                listingItem,
                price,
                System.currentTimeMillis()
        );

        listings.put(listing.getId(), listing);
        if (!save()) {
            listings.remove(listing.getId());
            seller.getInventory().addItem(listingItem);
            seller.updateInventory();
            return "&cCould not save auction listing. Your item was returned.";
        }

        return null;
    }

    public synchronized String createAuctionListing(Player seller, ItemStack source, int amount, double startingPrice, long durationSeconds) {
        if (source == null || source.getType().isAir()) {
            return "&cHold the item you want to list in your main hand.";
        }
        if (amount <= 0 || amount > source.getAmount()) {
            return "&cInvalid amount. Use up to the amount in your hand.";
        }
        if (startingPrice <= 0.0D) {
            return "&cStarting bid must be greater than 0.";
        }

        long minDuration = Math.max(30L, plugin.getConfig().getLong("ah.bidding.min-duration-seconds", 300L));
        long maxDuration = Math.max(minDuration, plugin.getConfig().getLong("ah.bidding.max-duration-seconds", 86400L));
        if (durationSeconds < minDuration || durationSeconds > maxDuration) {
            return "&cDuration must be between &e" + minDuration + "s &cand &e" + maxDuration + "s&c.";
        }

        if (plugin.isItemBlacklisted(source)) {
            return plugin.msg("item-blacklisted");
        }

        ItemStack listingItem = source.clone();
        listingItem.setAmount(amount);

        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != source.getType() || hand.getAmount() < amount) {
            return "&cYour main hand item changed. Try again.";
        }

        hand.setAmount(hand.getAmount() - amount);
        if (hand.getAmount() <= 0) {
            seller.getInventory().setItemInMainHand(null);
        } else {
            seller.getInventory().setItemInMainHand(hand);
        }
        seller.updateInventory();

        long now = System.currentTimeMillis();
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID(),
                seller.getUniqueId(),
                seller.getName(),
                listingItem,
                0.0D,
                now,
                AuctionListing.ListingType.AUCTION,
                startingPrice,
                0.0D,
                null,
                null,
                now + (durationSeconds * 1000L),
                0
        );

        listings.put(listing.getId(), listing);
        if (!save()) {
            listings.remove(listing.getId());
            seller.getInventory().addItem(listingItem);
            seller.updateInventory();
            return "&cCould not save auction listing. Your item was returned.";
        }

        return null;
    }

    public synchronized double getMinimumNextBid(AuctionListing listing) {
        if (listing == null || !listing.isAuction()) return 0.0D;

        double base = listing.getCurrentBid() > 0.0D ? listing.getCurrentBid() : listing.getStartingPrice();
        double minIncrement = Math.max(0.01D, plugin.getConfig().getDouble("ah.bidding.min-increment", 1.0D));
        double minIncrementPercent = Math.max(0.0D, plugin.getConfig().getDouble("ah.bidding.min-increment-percent", 0.0D));

        if (listing.getCurrentBid() <= 0.0D) {
            return roundMoney(base);
        }

        double byPercent = base * (minIncrementPercent / 100.0D);
        return roundMoney(base + Math.max(minIncrement, byPercent));
    }

    public synchronized BidResult placeBid(Player bidder, UUID listingId, double bidAmount) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null) {
            return BidResult.error("&cThat listing no longer exists.");
        }
        if (!listing.isAuction()) {
            return BidResult.error("&cThat listing is not a bidding auction.");
        }
        if (listing.getSellerUuid().equals(bidder.getUniqueId())) {
            return BidResult.error("&cYou cannot bid on your own listing.");
        }
        if (listing.getHighestBidderUuid() != null && listing.getHighestBidderUuid().equals(bidder.getUniqueId())) {
            return BidResult.error("&cYou are already the highest bidder.");
        }

        long now = System.currentTimeMillis();
        if (listing.getEndAt() > 0L && now >= listing.getEndAt()) {
            return BidResult.error("&cThat auction has already ended.");
        }

        double minNext = getMinimumNextBid(listing);
        if (bidAmount < minNext) {
            return BidResult.error("&cMinimum next bid is &e" + plugin.getCurrencyManager().format(minNext) + "&c.");
        }

        if (!plugin.getCurrencyManager().isAvailable()) {
            return BidResult.error(plugin.msg("no-currency-hook"));
        }
        if (!plugin.getCurrencyManager().has(bidder.getUniqueId(), bidAmount)) {
            return BidResult.error("&cYou need &e" + plugin.getCurrencyManager().format(bidAmount) + " &cto place that bid.");
        }
        if (!plugin.getCurrencyManager().take(bidder.getUniqueId(), bidAmount)) {
            return BidResult.error("&cPayment failed. Try again.");
        }

        long extendWindowSeconds = Math.max(0L, plugin.getConfig().getLong("ah.bidding.anti-snipe-window-seconds", 30L));
        long extendBySeconds = Math.max(0L, plugin.getConfig().getLong("ah.bidding.anti-snipe-extend-seconds", 30L));
        boolean extended = false;
        long newEndAt = listing.getEndAt();
        if (newEndAt > 0L && extendWindowSeconds > 0L && extendBySeconds > 0L) {
            long remainingMs = newEndAt - now;
            if (remainingMs > 0L && remainingMs <= (extendWindowSeconds * 1000L)) {
                newEndAt += extendBySeconds * 1000L;
                extended = true;
            }
        }

        AuctionListing updated = new AuctionListing(
                listing.getId(),
                listing.getSellerUuid(),
                listing.getSellerName(),
                listing.getItem(),
                listing.getPrice(),
                listing.getCreatedAt(),
                listing.getListingType(),
                listing.getStartingPrice(),
                roundMoney(bidAmount),
                bidder.getUniqueId(),
                bidder.getName(),
                newEndAt,
                listing.getBidCount() + 1
        );

        UUID previousBidder = listing.getHighestBidderUuid();
        double previousBid = listing.getCurrentBid();

        listings.put(updated.getId(), updated);
        if (!save()) {
            listings.put(listing.getId(), listing);
            plugin.getCurrencyManager().give(bidder.getUniqueId(), bidAmount);
            return BidResult.error("&cCould not save your bid. You were refunded.");
        }

        if (previousBidder != null && previousBid > 0.0D) {
            if (!plugin.getCurrencyManager().give(previousBidder, previousBid)) {
                plugin.getLogger().warning("Failed to auto-refund outbid player " + previousBidder + " for " + previousBid);
            }
        }

        return BidResult.success(roundMoney(bidAmount), extended);
    }

    public synchronized AuctionListing findListingByShortId(String shortId) {
        if (shortId == null || shortId.isBlank()) return null;
        String normalized = shortId.trim().toLowerCase();
        for (AuctionListing listing : listings.values()) {
            if (listing.getShortId().toLowerCase().equals(normalized)) {
                return listing;
            }
        }
        return null;
    }

    public synchronized PurchaseResult purchaseListing(Player buyer, UUID listingId) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null) {
            return PurchaseResult.error("&cThat listing no longer exists.");
        }
        if (listing.isAuction()) {
            return PurchaseResult.error("&cThat listing is an auction. Use &e/ah bid <id> <amount>&c.");
        }
        if (listing.getSellerUuid().equals(buyer.getUniqueId())) {
            return PurchaseResult.error("&cYou cannot buy your own listing.");
        }

        if (!plugin.getCurrencyManager().isAvailable()) {
            return PurchaseResult.error(plugin.msg("no-currency-hook"));
        }

        double price = listing.getPrice();
        if (!plugin.getCurrencyManager().has(buyer.getUniqueId(), price)) {
            return PurchaseResult.error("&cYou need &e" + plugin.getCurrencyManager().format(price) + " &cto buy this listing.");
        }

        if (!plugin.getCurrencyManager().take(buyer.getUniqueId(), price)) {
            return PurchaseResult.error("&cPayment failed. Try again.");
        }

        listings.remove(listingId);
        if (!plugin.getCurrencyManager().give(listing.getSellerUuid(), price)) {
            listings.put(listingId, listing);
            plugin.getCurrencyManager().give(buyer.getUniqueId(), price);
            return PurchaseResult.error("&cCould not pay seller, purchase was cancelled.");
        }

        ItemStack item = listing.getItem();
        boolean delivered = hasRoomFor(buyer, item);
        if (delivered) {
            buyer.getInventory().addItem(item.clone());
        } else {
            pendingClaims.computeIfAbsent(buyer.getUniqueId(), ignored -> new ArrayList<>()).add(item.clone());
        }

        PlayerStats sellerBefore = playerStats.get(listing.getSellerUuid());
        PlayerStats buyerBefore = playerStats.get(buyer.getUniqueId());
        recordPurchaseStats(listing.getSellerUuid(), buyer.getUniqueId(), price);

        if (!save()) {
            restoreStats(listing.getSellerUuid(), sellerBefore);
            restoreStats(buyer.getUniqueId(), buyerBefore);

            if (delivered) {
                removeOneMatchingFromInventory(buyer, item);
            } else {
                List<ItemStack> claims = pendingClaims.get(buyer.getUniqueId());
                if (claims != null && !claims.isEmpty()) {
                    claims.remove(claims.size() - 1);
                    if (claims.isEmpty()) pendingClaims.remove(buyer.getUniqueId());
                }
            }
            listings.put(listingId, listing);
            plugin.getCurrencyManager().take(listing.getSellerUuid(), price);
            plugin.getCurrencyManager().give(buyer.getUniqueId(), price);
            return PurchaseResult.error("&cPurchase save failed. Payment was refunded.");
        }

        return PurchaseResult.success(delivered, item);
    }

    public synchronized CancelResult cancelListing(Player actor, UUID listingId, boolean adminOverride) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null) {
            return CancelResult.error("&cThat listing no longer exists.");
        }

        if (!adminOverride && !listing.getSellerUuid().equals(actor.getUniqueId())) {
            return CancelResult.error("&cYou can only cancel your own listings.");
        }

        listings.remove(listingId);
        ItemStack item = listing.getItem();

        boolean delivered = false;
        Player sellerOnline = Bukkit.getPlayer(listing.getSellerUuid());
        if (sellerOnline != null && hasRoomFor(sellerOnline, item)) {
            sellerOnline.getInventory().addItem(item.clone());
            delivered = true;
        } else {
            pendingClaims.computeIfAbsent(listing.getSellerUuid(), ignored -> new ArrayList<>()).add(item.clone());
        }

        if (!save()) {
            listings.put(listingId, listing);
            if (delivered && sellerOnline != null) {
                removeOneMatchingFromInventory(sellerOnline, item);
            } else {
                List<ItemStack> claims = pendingClaims.get(listing.getSellerUuid());
                if (claims != null && !claims.isEmpty()) {
                    claims.remove(claims.size() - 1);
                    if (claims.isEmpty()) pendingClaims.remove(listing.getSellerUuid());
                }
            }
            return CancelResult.error("&cCould not save listing cancel.");
        }

        return CancelResult.success(delivered, item);
    }

    public synchronized int claimAll(Player player) {
        List<ItemStack> claims = pendingClaims.get(player.getUniqueId());
        if (claims == null || claims.isEmpty()) return 0;

        int claimed = 0;
        while (!claims.isEmpty()) {
            ItemStack item = claims.get(0);
            if (!hasRoomFor(player, item)) break;
            player.getInventory().addItem(item.clone());
            claims.remove(0);
            claimed++;
        }

        if (claims.isEmpty()) {
            pendingClaims.remove(player.getUniqueId());
        }

        save();
        return claimed;
    }

    private void recordPurchaseStats(UUID seller, UUID buyer, double price) {
        PlayerStats sellerStats = playerStats.getOrDefault(seller, new PlayerStats(0, 0, 0.0D, 0.0D));
        playerStats.put(seller, new PlayerStats(
                sellerStats.soldCount() + 1,
                sellerStats.boughtCount(),
                sellerStats.earned() + price,
                sellerStats.spent()
        ));

        PlayerStats buyerStats = playerStats.getOrDefault(buyer, new PlayerStats(0, 0, 0.0D, 0.0D));
        playerStats.put(buyer, new PlayerStats(
                buyerStats.soldCount(),
                buyerStats.boughtCount() + 1,
                buyerStats.earned(),
                buyerStats.spent() + price
        ));
    }

    private void restoreStats(UUID player, PlayerStats stats) {
        if (stats == null) {
            playerStats.remove(player);
        } else {
            playerStats.put(player, stats);
        }
    }

    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed closing auctions database", ex);
        } finally {
            connection = null;
        }
    }

    private byte[] serializeItem(ItemStack item) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            return bytes.toByteArray();
        }
    }

    private ItemStack deserializeItem(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream input = new BukkitObjectInputStream(inputBytes)) {
            return (ItemStack) input.readObject();
        }
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

    private static void removeOneMatchingFromInventory(Player player, ItemStack item) {
        int needed = item.getAmount();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null || current.getType().isAir()) continue;
            if (!current.isSimilar(item)) continue;

            int take = Math.min(current.getAmount(), needed);
            current.setAmount(current.getAmount() - take);
            if (current.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                player.getInventory().setItem(slot, current);
            }
            needed -= take;
            if (needed <= 0) return;
        }
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
