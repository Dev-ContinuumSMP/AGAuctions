package com.example.buyorders.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionListing {

    public enum ListingType {
        BUY_NOW,
        AUCTION
    }

    private final UUID id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long createdAt;
    private final ListingType listingType;
    private final double startingPrice;
    private final double currentBid;
    private final UUID highestBidderUuid;
    private final String highestBidderName;
    private final long endAt;
    private final int bidCount;

    public AuctionListing(UUID id, UUID sellerUuid, String sellerName, ItemStack item, double price, long createdAt) {
        this(id, sellerUuid, sellerName, item, price, createdAt, ListingType.BUY_NOW, 0.0D, 0.0D, null, null, 0L, 0);
    }

    public AuctionListing(
            UUID id,
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            double price,
            long createdAt,
            ListingType listingType,
            double startingPrice,
            double currentBid,
            UUID highestBidderUuid,
            String highestBidderName,
            long endAt,
            int bidCount
    ) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.createdAt = createdAt;
        this.listingType = listingType == null ? ListingType.BUY_NOW : listingType;
        this.startingPrice = startingPrice;
        this.currentBid = currentBid;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
        this.endAt = endAt;
        this.bidCount = Math.max(0, bidCount);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }

    public ListingType getListingType() {
        return listingType;
    }

    public boolean isAuction() {
        return listingType == ListingType.AUCTION;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public UUID getHighestBidderUuid() {
        return highestBidderUuid;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public long getEndAt() {
        return endAt;
    }

    public int getBidCount() {
        return bidCount;
    }

    public double getDisplayPrice() {
        if (!isAuction()) return price;
        if (currentBid > 0.0D) return currentBid;
        return Math.max(0.0D, startingPrice);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getShortId() {
        return id.toString().replace("-", "").substring(0, 6);
    }
}
