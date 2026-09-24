package com.districtx.pacifica.database;

import java.math.BigDecimal;

public final class ItemPrice {
    private final String itemKey;
    private final String material;
    private final String displayName;
    private final Integer customModelData;
    private final BigDecimal basePrice;
    private final long createdAt;
    private final long updatedAt;

    public ItemPrice(String itemKey, String material, String displayName, Integer customModelData, BigDecimal basePrice, long createdAt, long updatedAt) {
        this.itemKey = itemKey;
        this.material = material;
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.basePrice = basePrice;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getItemKey() { return itemKey; }
    public String getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public Integer getCustomModelData() { return customModelData; }
    public BigDecimal getBasePrice() { return basePrice; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
}