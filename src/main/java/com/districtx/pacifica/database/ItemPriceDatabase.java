package com.districtx.pacifica.database;

import com.districtx.pacifica.ConfigStore;
import com.districtx.pacifica.PacificaPlugin;
import com.cryptomorin.xseries.XMaterial;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemPriceDatabase extends AbstractSqliteDatabase {
    public ItemPriceDatabase(PacificaPlugin plugin) {
        super(plugin.paths().getItemPriceDatabase().toFile());
    }

    @Override
    public synchronized void createTables() {
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS item_prices (item_key TEXT PRIMARY KEY, material TEXT NOT NULL, display_name TEXT, custom_model_data INTEGER, base_price TEXT NOT NULL, identity_data BLOB, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create item price table", exception);
        }
    }

    public synchronized void setPrice(ItemStack item, BigDecimal price) {
        ItemStack identity = identityItem(item);
        setPrice(key(identity), identity, price);
    }

    public synchronized ItemPrice getPrice(ItemStack item) {
        ItemPrice custom = find(key(identityItem(item)));
        return custom == null ? find(materialKey(item.getType().name())) : custom;
    }

    public synchronized void migrateLegacyPrices(ConfigStore store) {
        try {
            if (hasMetadata("legacy-prices-v1")) {
                store.deleteLegacyPrices();
                return;
            }
            ConfigurationSection configuredItems = store.prices().getConfigurationSection("items");
            ConfigurationSection items = configuredItems == null ? store.trashcan().getConfigurationSection("default-prices") : configuredItems;
            if (items != null) {
                for (String materialKey : items.getKeys(false)) {
                    XMaterial.matchXMaterial(materialKey).map(XMaterial::parseMaterial).ifPresent(material -> {
                        double price = items.getDouble(materialKey, 0);
                        if (price > 0 && find(materialKey(material.name())) == null) setMaterialPrice(material, BigDecimal.valueOf(price));
                    });
                }
            }
            ConfigurationSection custom = store.prices().getConfigurationSection("custom-items");
            if (custom != null) {
                for (String id : custom.getKeys(false)) {
                    ConfigurationSection definition = custom.getConfigurationSection(id);
                    ItemStack item = definition == null ? null : definition.getItemStack("item");
                    double price = definition == null ? 0 : definition.getDouble("price", 0);
                    if (item != null && price > 0 && find(key(identityItem(item))) == null) setPrice(item, BigDecimal.valueOf(price));
                }
            }
            setMetadata("legacy-prices-v1", "1");
            store.deleteLegacyPrices();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not migrate legacy item prices", exception);
        }
    }

    private void setMaterialPrice(Material material, BigDecimal price) {
        long now = System.currentTimeMillis();
        upsert(materialKey(material.name()), material.name(), null, null, price, null, now);
    }

    private void setPrice(String itemKey, ItemStack identity, BigDecimal price) {
        ItemMeta meta = identity.getItemMeta();
        String displayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
        Integer customModelData = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        upsert(itemKey, identity.getType().name(), displayName, customModelData, price, identity.serializeAsBytes(), System.currentTimeMillis());
    }

    private void upsert(String itemKey, String material, String displayName, Integer customModelData, BigDecimal price, byte[] identity, long now) {
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Price must be positive");
        try (PreparedStatement statement = getConnection().prepareStatement("INSERT INTO item_prices(item_key, material, display_name, custom_model_data, base_price, identity_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(item_key) DO UPDATE SET material = excluded.material, display_name = excluded.display_name, custom_model_data = excluded.custom_model_data, base_price = excluded.base_price, identity_data = excluded.identity_data, updated_at = excluded.updated_at")) {
            statement.setString(1, itemKey);
            statement.setString(2, material);
            statement.setString(3, displayName);
            if (customModelData == null) statement.setObject(4, null); else statement.setInt(4, customModelData);
            statement.setString(5, price.toPlainString());
            statement.setBytes(6, identity);
            statement.setLong(7, now);
            statement.setLong(8, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save item price", exception);
        }
    }

    private ItemPrice find(String itemKey) {
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT item_key, material, display_name, custom_model_data, base_price, created_at, updated_at FROM item_prices WHERE item_key = ?")) {
            statement.setString(1, itemKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Integer customModelData = result.getObject("custom_model_data") == null ? null : result.getInt("custom_model_data");
                return new ItemPrice(result.getString("item_key"), result.getString("material"), result.getString("display_name"), customModelData, new BigDecimal(result.getString("base_price")), result.getLong("created_at"), result.getLong("updated_at"));
            }
        } catch (SQLException | NumberFormatException exception) {
            throw new IllegalStateException("Could not read item price", exception);
        }
    }

    private ItemStack identityItem(ItemStack item) {
        ItemStack identity = item.clone();
        identity.setAmount(1);
        ItemMeta meta = identity.getItemMeta();
        if (meta != null) {
            meta.setLore(null);
            if (meta instanceof Damageable damageable) damageable.setDamage(0);
            identity.setItemMeta(meta);
        }
        return identity;
    }

    private String key(ItemStack identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.serializeAsBytes());
            StringBuilder result = new StringBuilder("item:");
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String materialKey(String material) {
        return "material:" + material.toUpperCase(java.util.Locale.ROOT);
    }
}