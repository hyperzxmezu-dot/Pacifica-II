package com.districtx.pacifica.util;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.XPotion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class Items {
    private Items() { }

    public static ItemStack create(String material, String name, String... lore) {
        ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
        if (item == null) item = XMaterial.matchXMaterial("BARRIER").map(XMaterial::parseItem).orElse(null);
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> lines = new ArrayList<>();
            Arrays.stream(lore).map(Items::color).forEach(lines::add);
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack head(Player player, String name, String lore) {
        ItemStack item = create("PLAYER_HEAD", name, lore);
        if (item != null && item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    public static void clickSound(Player player, String sound) { XSound.matchXSound(sound).ifPresent(value -> value.play(player)); }
    public static void blindness(Player player, int ticks) {
        XPotion.matchXPotion("BLINDNESS").map(potion -> potion.buildPotionEffect(ticks, 0)).ifPresent(player::addPotionEffect);
    }
    public static void invisibility(Player player, int ticks) {
        XPotion.matchXPotion("INVISIBILITY").map(potion -> potion.buildPotionEffect(ticks, 0)).ifPresent(player::addPotionEffect);
    }
    public static void unbreaking(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        XEnchantment.matchXEnchantment("UNBREAKING").ifPresent(enchantment -> meta.addEnchant(enchantment.getEnchant(), 2, true));
        item.setItemMeta(meta);
    }
}