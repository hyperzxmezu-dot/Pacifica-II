package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public final class TagItemService {
    private final PacificaPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey idKey;

    public TagItemService(PacificaPlugin plugin) {
        this.plugin = plugin;
        String namespace = plugin.store().tag().getString("tag-system.physical-item.namespace", "pacifica");
        itemKey = new NamespacedKey(namespace, plugin.store().tag().getString("tag-system.physical-item.item-key", "tag_item"));
        idKey = new NamespacedKey(namespace, plugin.store().tag().getString("tag-system.physical-item.id-key", "tag_id"));
    }

    public ItemStack create(TagDefinition tag) {
        String material = plugin.store().tag().getString("tag-system.physical-item.material", "NAME_TAG");
        String name = plugin.store().tag().getString("tag-system.physical-item.name", "%tag%").replace("%tag%", tag.displayText());
        String rarity = rarity(tag);
        List<String> lore = plugin.store().tag().getStringList("tag-system.physical-item.lore");
        if (lore.isEmpty()) lore = List.of("&1Items", "&7", "&7Type: Tags", "&7Rarity: " + rarity, "&7", "&eRight-click to unlock");
        ItemStack item = Items.create(material, name, lore.stream().map(line -> line.replace("%rarity%", rarity)).toArray(String[]::new));
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, tag.getId());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTagItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)
                && meta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public void handleInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (!isTagItem(item)) return;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        Tag tag = plugin.tags().getTag(id);
        if (tag == null || plugin.tags().hasTag(player.getUniqueId(), id)) return;
        event.setCancelled(true);
        ItemStack unlockedItem = create((TagDefinition) tag);
        if (unlockedItem == null) return;
        if (item.getAmount() <= 0) return;
        if (!plugin.tags().giveTag(player.getUniqueId(), id)) return;
        item.setAmount(item.getAmount() - 1);
        String message = plugin.store().tag().getString("tag-system.messages.unlocked", "&7You unlocked %rarity% %tag%&7!")
                .replace("%rarity%", rarity((TagDefinition) tag)).replace("%tag%", ((TagDefinition) tag).displayText());
        TextComponent text = new TextComponent(Items.color(message + " "));
        TextComponent equip = new TextComponent(Items.color("&a&l[EQUIP]"));
        equip.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tag _equip " + tag.getId()));
        text.addExtra(equip);
        player.spigot().sendMessage(text);
    }

    private String rarity(TagDefinition tag) {
        return Items.color(plugin.store().tag().getString("tag-system.rarity." + tag.getRarity().name().toLowerCase(java.util.Locale.ROOT), tag.getRarity().name()));
    }
}