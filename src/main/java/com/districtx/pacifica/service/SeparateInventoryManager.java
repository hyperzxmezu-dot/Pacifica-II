package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SeparateInventoryManager {
    private final PacificaPlugin plugin;
    private final InventoryStorage storage;
    private final Set<UUID> switchingPlayers = new HashSet<>();
    private final Map<UUID, DeathState> deathStates = new HashMap<>();

    public SeparateInventoryManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        storage = new DatabaseInventoryStorage(plugin.playerDatabase());
    }

    public void saveInventory(Player player) {
        if (deathStates.containsKey(player.getUniqueId())) return;
        saveInventory(player.getUniqueId(), player.getGameMode(), player.getInventory().getContents());
    }

    public InventoryStorage storage() { return storage; }

    public void saveInventory(UUID uuid, GameMode gameMode, ItemStack[] contents) {
        if (!enabled(gameMode)) return;
        storage.save(uuid, gameMode, cloneContents(contents));
    }

    public void loadInventory(Player player) { loadInventory(player, player.getGameMode()); }

    public void loadInventory(Player player, GameMode gameMode) {
        if (!enabled(gameMode)) return;
        ItemStack[] contents = storage.load(player.getUniqueId(), gameMode);
        if (contents != null) player.getInventory().setContents(normalize(contents, player.getInventory().getSize()));
    }

    public void switchInventory(Player player, GameMode from, GameMode to) {
        if (from == to || !enabled(from) || !enabled(to) || !switchingPlayers.add(player.getUniqueId())) return;
        try {
            ItemStack[] target = storage.load(player.getUniqueId(), to);
            if (target == null) target = new ItemStack[player.getInventory().getSize()];
            saveInventory(player.getUniqueId(), from, player.getInventory().getContents());
            player.getInventory().clear();
            player.getInventory().setContents(normalize(target, player.getInventory().getSize()));
            player.updateInventory();
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not switch inventory for " + player.getName() + ": " + exception.getMessage());
        } finally {
            switchingPlayers.remove(player.getUniqueId());
        }
    }

    public void handlePlayerDeath(Player player, ItemStack[] protectedContents) {
        UUID uuid = player.getUniqueId();
        ItemStack[] saved = normalize(protectedContents, player.getInventory().getSize());
        deathStates.put(uuid, new DeathState(player.getGameMode(), saved));
        saveInventory(uuid, player.getGameMode(), saved);
    }

    public void handlePlayerRespawn(Player player) {
        DeathState state = deathStates.get(player.getUniqueId());
        if (state == null) return;
        ItemStack[] retained = protectedItemsIn(player.getInventory().getContents(), state.protectedContents);
        ItemStack[] restored = mergeProtected(retained, state.protectedContents, player.getInventory().getSize());
        player.getInventory().clear();
        player.getInventory().setContents(restored);
        player.updateInventory();
    }

    public void finishDeathWithoutModeChange(Player player) {
        DeathState state = deathStates.remove(player.getUniqueId());
        if (state == null) return;
        ItemStack[] retained = protectedItemsIn(player.getInventory().getContents(), state.protectedContents);
        ItemStack[] restored = mergeProtected(retained, state.protectedContents, player.getInventory().getSize());
        player.getInventory().clear();
        player.getInventory().setContents(restored);
        saveInventory(player.getUniqueId(), player.getGameMode(), restored);
        player.updateInventory();
    }

    public boolean isDeathTransition(Player player) {
        return deathStates.containsKey(player.getUniqueId());
    }

    public void completeDeathTransition(Player player, GameMode targetMode) {
        UUID uuid = player.getUniqueId();
        DeathState state = deathStates.remove(uuid);
        if (state == null) return;

        ItemStack[] target = enabled(targetMode) ? storage.load(uuid, targetMode) : null;
        ItemStack[] retained = protectedItemsIn(player.getInventory().getContents(), state.protectedContents);
        ItemStack[] merged = mergeProtected(target, retained, player.getInventory().getSize());
        merged = mergeProtected(merged, state.protectedContents, player.getInventory().getSize());
        if (enabled(targetMode)) saveInventory(uuid, targetMode, merged);
        player.getInventory().clear();
        player.getInventory().setContents(merged);
        player.updateInventory();
    }

    public ItemStack[] getInventory(UUID uuid, GameMode gameMode) { return storage.load(uuid, gameMode); }
    public boolean hasInventory(UUID uuid, GameMode gameMode) { return storage.exists(uuid, gameMode); }

    public void initialize(Player player) {
        if (!enabled(player.getGameMode())) return;
        DeathState deathState = deathStates.remove(player.getUniqueId());
        if (deathState != null) {
            player.getInventory().clear();
            player.getInventory().setContents(normalize(deathState.protectedContents, player.getInventory().getSize()));
            player.updateInventory();
            return;
        }
        if (hasInventory(player.getUniqueId(), player.getGameMode())) loadInventory(player);
        else saveInventory(player);
    }

    public void saveOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) saveInventory(player);
    }

    private boolean enabled(GameMode mode) {
        return plugin.store().separateInventory().getBoolean("separate-inventory.enabled", true)
                && plugin.store().separateInventory().getBoolean("separate-inventory.modes." + mode.name().toLowerCase(), true);
    }

    private ItemStack[] normalize(ItemStack[] contents, int size) {
        ItemStack[] result = new ItemStack[size];
        System.arraycopy(contents, 0, result, 0, Math.min(contents.length, size));
        return result;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] result = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) result[i] = contents[i] == null ? null : contents[i].clone();
        return result;
    }

    private ItemStack[] mergeProtected(ItemStack[] target, ItemStack[] protectedContents, int size) {
        ItemStack[] result = target == null ? new ItemStack[size] : normalize(target, size);
        List<ItemStack> processed = new ArrayList<>();
        for (int sourceSlot = 0; sourceSlot < protectedContents.length; sourceSlot++) {
            ItemStack item = protectedContents[sourceSlot];
            if (item == null) continue;
            if (processed.stream().anyMatch(processedItem -> processedItem.isSimilar(item))) continue;
            processed.add(item);

            int protectedAmount = 0;
            for (ItemStack protectedItem : protectedContents) {
                if (protectedItem != null && protectedItem.isSimilar(item)) protectedAmount += protectedItem.getAmount();
            }
            int remaining = Math.max(0, protectedAmount - countSimilar(result, item));

            for (int slot = 0; slot < result.length && remaining > 0; slot++) {
                ItemStack existing = result[slot];
                if (existing == null || !existing.isSimilar(item)) continue;
                int transferable = Math.min(remaining, Math.max(0, existing.getMaxStackSize() - existing.getAmount()));
                existing.setAmount(existing.getAmount() + transferable);
                remaining -= transferable;
            }

            while (remaining > 0) {
                int targetSlot = sourceSlot < result.length && result[sourceSlot] == null ? sourceSlot : firstEmpty(result);
                if (targetSlot < 0) break;
                ItemStack addition = item.clone();
                int amount = Math.min(remaining, addition.getMaxStackSize());
                addition.setAmount(amount);
                result[targetSlot] = addition;
                remaining -= amount;
            }
        }
        return result;
    }

    private ItemStack[] protectedItemsIn(ItemStack[] contents, ItemStack[] protectedContents) {
        ItemStack[] retained = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) continue;
            for (ItemStack protectedItem : protectedContents) {
                if (protectedItem != null && protectedItem.isSimilar(item)) {
                    retained[slot] = item.clone();
                    break;
                }
            }
        }
        return retained;
    }

    private int countSimilar(ItemStack[] contents, ItemStack item) {
        int amount = 0;
        for (ItemStack existing : contents) {
            if (existing != null && existing.isSimilar(item)) amount += existing.getAmount();
        }
        return amount;
    }

    private int firstEmpty(ItemStack[] contents) {
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] == null) return slot;
        }
        return -1;
    }

    private static final class DeathState {
        private final GameMode deathMode;
        private final ItemStack[] protectedContents;

        private DeathState(GameMode deathMode, ItemStack[] protectedContents) {
            this.deathMode = deathMode;
            this.protectedContents = protectedContents;
        }
    }
}