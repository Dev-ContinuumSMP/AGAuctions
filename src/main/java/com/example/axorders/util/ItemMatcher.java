package com.example.axorders.util;

import com.example.axorders.AxOrdersAddon;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.UUID;

public class ItemMatcher {
    
    private static final String CUSTOM_ITEM_ID_KEY = "custom_item_id";
    private static final Set<NamespacedKey> DEFAULT_IDENTITY_KEYS = Set.of(
            new NamespacedKey("axorders", CUSTOM_ITEM_ID_KEY),
            new NamespacedKey("axordersaddon", CUSTOM_ITEM_ID_KEY),
            new NamespacedKey("pyrofishing", "pyro_item")
    );
    private static final Set<String> DEFAULT_IDENTITY_NAMESPACES = Set.of("mcmmo");
    
    /**
     * Checks if two ItemStacks match exactly, using custom IDs when the required item has one
     * @param submitted The item submitted by the player
     * @param required The item template from the buy order
     * @return true if items match, false otherwise
     */
    public static boolean matchesCustomItem(ItemStack submitted, ItemStack required) {
        logItemComparison(submitted, required);

        // Handle null items
        if (submitted == null && required == null) return true;
        if (submitted == null || required == null) return false;

        Map<NamespacedKey, Object> requiredIds = getIdentityPersistentData(required);
        if (!requiredIds.isEmpty()) {
            Map<NamespacedKey, Object> submittedIds = getIdentityPersistentData(submitted);
            for (Map.Entry<NamespacedKey, Object> requiredId : requiredIds.entrySet()) {
                if (!requiredId.getValue().equals(submittedIds.get(requiredId.getKey()))) {
                    return false;
                }
            }

            return submitted.getType() == required.getType();
        }

        // Only vanilla/player-customized items without PDC identifiers may fall back to normal similarity.
        return submitted.isSimilar(required);
    }
    
    /**
     * Gets the custom item ID from PersistentDataContainer
     */
    public static String getCustomItemId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        return pdc.get(
            new org.bukkit.NamespacedKey(AxOrdersAddon.getPlugin(AxOrdersAddon.class), CUSTOM_ITEM_ID_KEY),
            PersistentDataType.STRING
        );
    }

    public static Map<String, String> describeStringPersistentData(ItemStack item) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<NamespacedKey, Object> entry : getIdentityPersistentData(item).entrySet()) {
            values.put(entry.getKey().toString(), String.valueOf(entry.getValue()));
        }
        return values;
    }

    public static Set<NamespacedKey> getPersistentDataKeys(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return Set.of();
        return item.getItemMeta().getPersistentDataContainer().getKeys();
    }

    private static Map<NamespacedKey, Object> getIdentityPersistentData(ItemStack item) {
        Map<NamespacedKey, Object> values = new LinkedHashMap<>();
        if (item == null || item.getItemMeta() == null) return values;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : getIdentityKeys()) {
            Object value = getIdentityValue(pdc, key);
            if (value != null) values.put(key, value);
        }
        for (NamespacedKey key : pdc.getKeys()) {
            if (!getIdentityNamespaces().contains(key.getNamespace())) continue;

            Object value = getIdentityValue(pdc, key);
            if (value != null) values.put(key, value);
        }
        return values;
    }

    private static Set<NamespacedKey> getIdentityKeys() {
        Set<NamespacedKey> keys = new HashSet<>(DEFAULT_IDENTITY_KEYS);
        try {
            AxOrdersAddon plugin = AxOrdersAddon.getPlugin(AxOrdersAddon.class);
            keys.add(new NamespacedKey(plugin, CUSTOM_ITEM_ID_KEY));

            List<String> configuredKeys = plugin
                    .getConfig()
                    .getStringList("custom-item-identity-keys");
            for (String configuredKey : configuredKeys) {
                NamespacedKey key = NamespacedKey.fromString(configuredKey);
                if (key != null) keys.add(key);
            }
        } catch (IllegalStateException ignored) {
            // Bukkit may not have a plugin instance in unit-style tests.
        }
        return keys;
    }

    private static Set<String> getIdentityNamespaces() {
        Set<String> namespaces = new HashSet<>(DEFAULT_IDENTITY_NAMESPACES);
        try {
            AxOrdersAddon plugin = AxOrdersAddon.getPlugin(AxOrdersAddon.class);
            for (String configuredKey : plugin.getConfig().getStringList("custom-item-identity-keys")) {
                String normalized = configuredKey == null ? "" : configuredKey.trim().toLowerCase();
                if (normalized.endsWith(":*")) {
                    String namespace = normalized.substring(0, normalized.length() - 2);
                    if (!namespace.isBlank()) namespaces.add(namespace);
                }
            }
        } catch (IllegalStateException ignored) {
            // Bukkit may not have a plugin instance in unit-style tests.
        }
        return namespaces;
    }

    private static Object getIdentityValue(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.STRING)) return pdc.get(key, PersistentDataType.STRING);
        if (pdc.has(key, PersistentDataType.INTEGER)) return pdc.get(key, PersistentDataType.INTEGER);
        if (pdc.has(key, PersistentDataType.LONG)) return pdc.get(key, PersistentDataType.LONG);
        if (pdc.has(key, PersistentDataType.DOUBLE)) return pdc.get(key, PersistentDataType.DOUBLE);
        return null;
    }

    private static void logItemComparison(ItemStack submitted, ItemStack required) {
        try {
            AxOrdersAddon plugin = AxOrdersAddon.getPlugin(AxOrdersAddon.class);
            if (!plugin.getConfig().getBoolean("debug-item-matching", false)) return;

            Logger logger = plugin.getLogger();
            logger.info("ItemMatcher.matchesCustomItem debug");
            logItem(logger, "REQUIRED ITEM", required);
            logItem(logger, "SUBMITTED ITEM", submitted);
        } catch (IllegalStateException ignored) {
            // Bukkit may not have a plugin instance in unit-style tests.
        }
    }

    private static void logItem(Logger logger, String label, ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        logger.info(label + ":");
        logger.info("Material: " + (item == null ? "null" : item.getType()));
        logger.info("Name: " + (meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "null"));
        logger.info("Lore: " + (meta != null && meta.hasLore() ? meta.getLore() : "null"));
        logger.info("CustomModelData: " + (meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : "null"));
        logger.info("PDC keys: " + getPersistentDataKeys(item));
    }
    
    /**
     * Compares two lists for equality (handles nulls)
     */
    private static boolean listsEqual(java.util.List<String> list1, java.util.List<String> list2) {
        if (list1 == null && list2 == null) return true;
        if (list1 == null || list2 == null) return false;
        if (list1.size() != list2.size()) return false;
        
        for (int i = 0; i < list1.size(); i++) {
            if (!list1.get(i).equals(list2.get(i))) return false;
        }
        return true;
    }
    
    /**
     * Compares enchantments between two ItemMeta objects
     */
    private static boolean enchantmentsEqual(ItemMeta meta1, ItemMeta meta2) {
        // If both have no enchantments, they match
        if (!meta1.hasEnchants() && !meta2.hasEnchants()) return true;
        
        // If only one has enchantments, they don't match
        if (meta1.hasEnchants() != meta2.hasEnchants()) return false;
        
        // Compare enchantments
        java.util.Set<org.bukkit.enchantments.Enchantment> enchants1 = meta1.getEnchants().keySet();
        java.util.Set<org.bukkit.enchantments.Enchantment> enchants2 = meta2.getEnchants().keySet();
        
        if (enchants1.size() != enchants2.size()) return false;
        
        for (org.bukkit.enchantments.Enchantment enchant : enchants1) {
            if (!meta2.hasEnchant(enchant)) return false;
            if (meta1.getEnchantLevel(enchant) != meta2.getEnchantLevel(enchant)) return false;
        }
        
        return true;
    }
    
    /**
     * Creates a custom item ID for use with PersistentDataContainer
     */
    public static String createCustomItemId() {
        return "custom_item_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Assigns a custom ID to an item for use with buy orders
     */
    public static void assignCustomId(ItemStack item, String customId) {
        if (item == null || item.getItemMeta() == null) return;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        pdc.set(
            new org.bukkit.NamespacedKey(AxOrdersAddon.getPlugin(AxOrdersAddon.class), CUSTOM_ITEM_ID_KEY),
            PersistentDataType.STRING,
            customId
        );
        
        item.setItemMeta(meta);
    }
}
