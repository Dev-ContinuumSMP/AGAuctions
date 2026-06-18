package com.example.axorders.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BuyOrder {

    private final UUID id;
    private final UUID buyerUuid;
    private final String buyerName;
    private final ItemStack itemTemplate;
    private final int quantityWanted;
    private int quantityFilled;
    private final double priceEach;
    private final long createdAt;

    public BuyOrder(UUID id, UUID buyerUuid, String buyerName, ItemStack itemTemplate, int quantityWanted,
                    int quantityFilled, double priceEach, long createdAt) {
        this.id = id;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.itemTemplate = itemTemplate.clone();
        this.itemTemplate.setAmount(1);
        this.quantityWanted = quantityWanted;
        this.quantityFilled = quantityFilled;
        this.priceEach = priceEach;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getBuyerUuid() { return buyerUuid; }
    public String getBuyerName() { return buyerName; }
    public ItemStack getItemTemplate() { return itemTemplate.clone(); }
    public Material getMaterial() { return itemTemplate.getType(); }
    public int getQuantityWanted() { return quantityWanted; }
    public int getQuantityFilled() { return quantityFilled; }
    public void setQuantityFilled(int quantityFilled) { this.quantityFilled = quantityFilled; }
    public double getPriceEach() { return priceEach; }
    public long getCreatedAt() { return createdAt; }
    public int getRemaining() { return quantityWanted - quantityFilled; }
    public boolean isFulfilled() { return getRemaining() <= 0; }

    public String getShortId() {
        return id.toString().replace("-", "").substring(0, 6);
    }
}
