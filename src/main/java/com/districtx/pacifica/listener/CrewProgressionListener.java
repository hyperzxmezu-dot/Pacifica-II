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
import org.bukkit.projectiles.ProjectileSource;

import java.util.concurrent.TimeUnit;

public final class CrewProgressionListener implements Listener {
    private final PacificaPlugin plugin;

    public CrewProgressionListener(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || !victim.getWorld().getPVP()
                || killer.hasMetadata("NPC") || victim.hasMetadata("NPC")) return;
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (!(lastDamage instanceof EntityDamageByEntityEvent damage)) return;
        Player finalAttacker = resolveAttacker(damage.getDamager());
        if (finalAttacker == null || !finalAttacker.getUniqueId().equals(killer.getUniqueId())) return;
        long baseXp = Math.max(1, plugin.store().config().getLong("crew.progression.player-kill-xp", 10));
        long cooldownHours = Math.max(1, plugin.store().config().getLong("crew.progression.kill-cooldown-hours", 24));
        long cooldownMillis = TimeUnit.HOURS.toMillis(cooldownHours);
        plugin.crews().recordPlayerKill(killer.getUniqueId(), victim.getUniqueId(), baseXp, cooldownMillis);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (!(damager instanceof Projectile projectile)) return null;
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player ? player : null;
    }
}