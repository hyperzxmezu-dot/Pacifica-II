package com.districtx.pacifica.listener;

import com.cryptomorin.xseries.XMaterial;
import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.gui.TaxiMenu;
import com.districtx.pacifica.gui.TrashMenu;
import com.districtx.pacifica.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class PacificaListener implements Listener {
    private final PacificaPlugin plugin;
    public PacificaListener(PacificaPlugin plugin) { this.plugin = plugin; }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer(); plugin.statuses().clear(player);
        Bukkit.getScheduler().runTask(plugin, () -> { if (!player.isOnline()) return; org.bukkit.Location spawn = plugin.store().spawn(); player.teleport(spawn == null ? player.getWorld().getSpawnLocation() : spawn); });
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location wastedLocation = plugin.wasted().deathLocation(player);
        if (wastedLocation != null) {
            event.setRespawnLocation(wastedLocation);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                plugin.separateInventories().handlePlayerRespawn(player);
                if (plugin.wasted().isWasted(player)) {
                    player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    plugin.wasted().track(player);
                }
            });
            return;
        }
        org.bukkit.Location spawn = plugin.store().spawn();
        if (spawn != null) event.setRespawnLocation(spawn);
        plugin.statuses().clear(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.separateInventories().handlePlayerRespawn(player);
                plugin.separateInventories().finishDeathWithoutModeChange(player);
            }
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) { plugin.teleports().cancel(event.getPlayer(), null); plugin.tpa().playerQuit(event.getPlayer()); plugin.statuses().clear(event.getPlayer()); }
    @EventHandler public void taxi(PlayerInteractEntityEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Entity entity = event.getRightClicked(); String plain = entity.getCustomName() == null ? "" : ChatColor.stripColor(entity.getCustomName());
        if (entity.getUniqueId().equals(plugin.store().taxiNpc()) || plain.equalsIgnoreCase("Taxi")) { event.setCancelled(true); if (!plugin.wasted().isWasted(player) && !plugin.combatTags().isTagged(player)) plugin.guis().open(new TaxiMenu(plugin, player)); }
    }
    @EventHandler public void trash(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || !plugin.store().isTrashCan(event.getClickedBlock().getLocation())) return;
        event.setCancelled(true);
        if (plugin.wasted().isWasted(event.getPlayer())) return;
        if (!event.getPlayer().hasPermission(plugin.store().trashcan().getString("trashcan.use-permission", "pacifica.trashcan.use"))) {
            event.getPlayer().sendMessage(plugin.store().message(event.getPlayer(), "trash.no-permission"));
            return;
        }
        plugin.guis().open(new TrashMenu(plugin, event.getPlayer()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void safeSpawnItemUse(PlayerInteractEvent event) {
        if (plugin.safeSpawn().contains(event.getPlayer().getLocation()) && protectedItem(event.getItem())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void safeSpawnEntityUse(PlayerInteractEntityEvent event) {
        if (plugin.safeSpawn().contains(event.getPlayer().getLocation()) && protectedItem(event.getPlayer().getInventory().getItemInMainHand())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void safeSpawnProjectile(ProjectileLaunchEvent event) {
        ProjectileSource source = event.getEntity().getShooter();
        if (source instanceof Player player && plugin.safeSpawn().contains(player.getLocation())
                && (protectedItem(player.getInventory().getItemInMainHand()) || protectedItem(player.getInventory().getItemInOffHand()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void death(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent.DamageCause cause = player.getLastDamageCause() == null ? null : player.getLastDamageCause().getCause();
        String type = player.getKiller() != null ? "pvp" : cause == EntityDamageEvent.DamageCause.STARVATION ? "starvation" : cause == EntityDamageEvent.DamageCause.FALL ? "fall" : "pvp";
        ItemStack[] protectedContents = new ItemStack[player.getInventory().getSize()];
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && slot < protectedContents.length; slot++) {
            if (protectedItem(contents[slot])) protectedContents[slot] = contents[slot].clone();
        }
        event.getDrops().removeIf(this::protectedItem);
        plugin.separateInventories().handlePlayerDeath(player, protectedContents);
        event.setDeathMessage(null);
        plugin.wasted().start(player, type, player.getKiller());
    }
    @EventHandler
    public void move(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (!plugin.wasted().isWasted(player)) {
            plugin.teleports().handleSafeSpawnMovement(player, from, to);
            return;
        }
        
        if (to != null) {
            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());
            event.setTo(to);
        }
        plugin.wasted().track(event.getPlayer());
    }
    @EventHandler public void wastedSneak(PlayerToggleSneakEvent event) { if (plugin.wasted().isWasted(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void wastedFlight(PlayerToggleFlightEvent event) { if (plugin.wasted().isWasted(event.getPlayer())) event.setCancelled(true); }
    @EventHandler
    public void wastedTeleport(PlayerTeleportEvent event) {
        if (plugin.wasted().isWasted(event.getPlayer()) && !plugin.wasted().isInternalTeleport(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void locationAwareTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.wasted().isWasted(player) || plugin.wasted().isInternalTeleport(player) || plugin.teleports().isManagedTeleport(player)) return;
        Location destination = event.getTo();
        if (destination != null) plugin.teleports().handleExternalTeleport(player, destination);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void wastedCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (!plugin.wasted().isWasted(event.getPlayer())) return;
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (!command.equals("ct")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.store().message(event.getPlayer(), "wasted.command-blocked"));
        }
    }
    private boolean protectedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        java.util.List<String> configured = new java.util.ArrayList<>(plugin.store().protection().getStringList("protection.protected-items"));
        if (plugin.crackShotEnabled()) configured.addAll(plugin.store().protection().getStringList("protection.weapons.crackshot"));
        if (plugin.weaponMechanicsEnabled()) configured.addAll(plugin.store().protection().getStringList("protection.weapons.weaponmechanics"));
        configured.addAll(plugin.store().protection().getStringList("protection.weapons.minecraft"));
        java.util.Set<String> names = new java.util.HashSet<>();
        names.add(XMaterial.matchXMaterial(item.getType()).name());
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) names.add(ChatColor.stripColor(meta.getDisplayName()));
            if (meta.hasLocalizedName()) names.add(meta.getLocalizedName());
        }
        return configured.stream().map(value -> ChatColor.stripColor(value).trim().toUpperCase(java.util.Locale.ROOT))
                .anyMatch(value -> names.stream().map(name -> name.trim().toUpperCase(java.util.Locale.ROOT)).anyMatch(value::equals));
    }
}