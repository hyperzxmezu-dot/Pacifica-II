package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class PlaceBountiesMenu extends JobDetailMenu {
    public PlaceBountiesMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&5&lPlace Bounties");
        standardFrame("GRAY_STAINED_GLASS_PANE");

        UUID targetId = plugin.jobMenus().selectedBountyTarget(player);
        if (targetId == null) {
            mainButton(11, "PLAYER_HEAD", "&5&lChoose Player", "&7Pick your target!");
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
            String targetName = target.getName() == null ? targetId.toString() : target.getName();
            ItemStack head = Items.create("PLAYER_HEAD", "&5&l" + targetName, "&7Picked target!");
            if (head != null && head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(target);
                head.setItemMeta(meta);
            }
            set(11, head);
        }

        BigDecimal amount = plugin.jobMenus().selectedBountyAmount(player);
        if (amount == null) mainButton(15, "PAPER", "&5&lChoose Amount", "&7Name your price!");
        else mainButton(15, "PAPER", "&5&l$" + amount.stripTrailingZeros().toPlainString(), "&7Selected amount");
        mainButton(49, "SLIME_BALL", "&5&lConfirm Bounty", "&7Click to place the hit!");
        boolean anonymous = plugin.jobMenus().isBountyAnonymous(player);
        mainButton(51, "WITHER_SKELETON_SKULL", "&5&lPlace Anonymously", "&7No one will know!",
                anonymous ? "&7Anonymous: &a&lEnabled" : "&7Anonymous: &c&lDisabled");
        mainButton(47, "REDSTONE", "&c&lBack", "&7Return to the &a&lJob Menu&7!");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (isAction(event, 11, "PLAYER_HEAD")) plugin.jobMenus().openChoosePlayerMenu(player);
        else if (isAction(event, 15, "PAPER")) plugin.jobMenus().openBountyAmountInput(player);
        else if (isAction(event, 49, "SLIME_BALL")) plugin.jobMenus().confirmPlayerBounty(player);
        else if (isAction(event, 51, "WITHER_SKELETON_SKULL")) plugin.jobMenus().toggleBountyAnonymity(player);
        else if (isAction(event, 47, "REDSTONE")) plugin.jobMenus().openHitmanMenu(player);
    }
}