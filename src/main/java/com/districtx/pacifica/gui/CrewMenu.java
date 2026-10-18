package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.crew.Crew;
import com.districtx.pacifica.api.crew.CrewMember;
import com.districtx.pacifica.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Comparator;
import java.util.List;

public final class CrewMenu extends BaseMenu {
    private static final int[] MEMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    public CrewMenu(PacificaPlugin plugin, Player player, int page) {
        super(plugin, player, "&8Crew Menu", page);
        Crew crew = plugin.crews().getCrewForPlayer(player.getUniqueId()).orElse(null);
        if (crew == null) {
            set(22, Items.create("BARRIER", "&cYou are not in a crew"));
        } else {
            set(4, Items.create("BOOK", "&6&l" + crew.name(), "&7Leader: &f" + playerName(crew.leaderId()),
                    "&7Members: &f" + crew.members().size() + "/" + crew.memberLimit()));
            set(20, Items.create("EXPERIENCE_BOTTLE", "&bCrew Level &f" + crew.level(),
                    "&7Crew XP: &f" + crew.crewXp()));
            set(22, Items.create("GOLD_INGOT", "&6Street Cred &f" + crew.streetCred()));
            set(24, Items.create("CHEST", "&aCrew Bank &f$" + crew.bankBalance().toPlainString()));
            List<CrewMember> members = crew.members().values().stream()
                    .sorted(Comparator.comparing(CrewMember::rank).thenComparing(member -> playerName(member.playerId()), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            int first = page * MEMBER_SLOTS.length;
            for (int index = first; index < Math.min(first + MEMBER_SLOTS.length, members.size()); index++) {
                CrewMember member = members.get(index);
                Player online = Bukkit.getPlayer(member.playerId());
                String name = playerName(member.playerId());
                set(MEMBER_SLOTS[index - first], Items.create("PAPER", "&e" + name,
                        "&7Rank: &f" + member.rank().name().replace('_', ' '),
                        "&7Status: " + (online == null ? "&cOffline" : "&aOnline"),
                        "&7Contribution: &f" + member.contributedXp() + " Crew XP"));
            }
            if (page > 0) set(48, Items.create("ARROW", "&ePrevious Page"));
            if (first + MEMBER_SLOTS.length < members.size()) set(50, Items.create("ARROW", "&eNext Page"));
        }
        set(49, Items.create("BARRIER", "&cClose"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
        } else if (slot == 48 && page > 0) {
            open(new CrewMenu(plugin, player, page - 1));
        } else if (slot == 50) {
            open(new CrewMenu(plugin, player, page + 1));
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    private static String playerName(java.util.UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }
}