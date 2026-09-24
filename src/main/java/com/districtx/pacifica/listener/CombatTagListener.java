package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatTagListener implements Listener {
    private final PacificaPlugin plugin;

    public CombatTagListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (plugin.safeSpawn().contains(attacker.getLocation()) || plugin.statuses().isSpawnProtected(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.safeSpawn().contains(victim.getLocation()) || plugin.statuses().isSpawnProtected(victim)) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.store().message(attacker, "spawn-protection.target"));
            return;
        }
        plugin.combatTags().tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void safeSpawnDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && (plugin.safeSpawn().contains(player.getLocation()) || plugin.statuses().isSpawnProtected(player))) {
            event.setCancelled(true);
        }
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        try {
            Object source = entity.getClass().getMethod("getSource").invoke(entity);
            if (source instanceof Player player) return player;
            if (source instanceof Entity sourceEntity) return attacker(sourceEntity);
        } catch (ReflectiveOperationException ignored) { }
        return null;
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { plugin.combatTags().handleQuit(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) { plugin.combatTags().handleDeath(event.getEntity(), event.getEntity().getKiller()); }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.combatTags().isTagged(player) && !plugin.combatTags().isAllowedCommand(event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage(plugin.store().message(player, "combattag.command-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (plugin.combatTags().isTagged(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.store().message(event.getPlayer(), "combattag.teleport-blocked"));
        }
    }
}