package com.districtx.pacifica.service;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JobType;
import com.districtx.pacifica.api.jobs.MedicAPI;
import com.districtx.pacifica.api.jobs.MedicResult;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

/** Handles the PDC-identified Medic syringe and configured kit-effect counters. */
public final class MedicManager implements MedicAPI, Listener {
    private static final List<String> KITS = List.of("tank", "flash", "pillow", "breath");
    private final PacificaPlugin plugin;
    private final NamespacedKey syringeKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public MedicManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        this.syringeKey = new NamespacedKey(plugin, "medic_syringe");
    }

    @Override public ItemStack createSyringe() {
        ItemStack item = XMaterial.matchXMaterial(plugin.store().config().getString("medic.syringe.material", "POTION"))
                .map(XMaterial::parseItem).orElse(null);
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.setDisplayName(Items.color(plugin.store().config().getString("medic.syringe.name", "&dMedic Syringe")));
        meta.getPersistentDataContainer().set(syringeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @Override public boolean isSyringe(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(syringeKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    @Override public MedicResult useSyringe(Player medic, Player target) {
        if (medic == null || target == null || !medic.isOnline() || !target.isOnline()) return MedicResult.INVALID;
        if (plugin.jails().isJailed(medic) || plugin.jails().isJailed(target)) return MedicResult.INVALID_TARGET;
        if (plugin.jobsAndWanted().getJob(medic) != JobType.MEDIC || !medic.hasPermission("pacifica.medic.use")) return MedicResult.NOT_MEDIC;
        ItemStack syringe = medic.getInventory().getItemInMainHand();
        if (!isSyringe(syringe)) return MedicResult.NO_SYRINGE;
        if (medic == target && !plugin.store().config().getBoolean("medic.allow-self", false)) return MedicResult.INVALID_TARGET;
        if (!medic.getWorld().equals(target.getWorld())
                || medic.getLocation().distanceSquared(target.getLocation()) > Math.pow(plugin.store().config().getDouble("medic.range", 4.0D), 2)) return MedicResult.INVALID_TARGET;
        long now = System.currentTimeMillis();
        long cooldownUntil = Math.max(cooldowns.getOrDefault(medic.getUniqueId(), 0L),
                plugin.store().data().getLong("medic.cooldowns." + medic.getUniqueId(), 0L));
        if (cooldownUntil > now) return MedicResult.COOLDOWN;
        KitEffects matched = findActiveKitEffects(target);
        if (matched == null) return MedicResult.NO_KIT_EFFECT;
        for (PotionEffectType effectType : matched.effects) target.removePotionEffect(effectType);
        applyConfiguredDebuffs(target);
        consumeOne(medic, syringe);
        long nextUse = now + durationMillis(plugin.store().config().getString("medic.cooldown", "30s"), 30_000L);
        cooldowns.put(medic.getUniqueId(), nextUse);
        plugin.store().data().set("medic.cooldowns." + medic.getUniqueId(), nextUse);
        plugin.store().save();
        BigDecimal reward = reward(matched.kit);
        if (reward.signum() > 0) plugin.economy().deposit(medic, reward);
        medic.sendMessage(Items.color("&aYou countered the " + matched.kit + " kit."));
        return MedicResult.SUCCESS;
    }

    @Override public List<Player> getActiveMedicKitPlayers() {
        List<Player> active = new ArrayList<>();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.jobsAndWanted().getJob(player) != JobType.MEDIC) continue;
            for (ItemStack item : player.getInventory().getContents()) {
                if (isSyringe(item)) { active.add(player); break; }
            }
        }
        return List.copyOf(active);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target) || !isSyringe(event.getPlayer().getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        MedicResult result = useSyringe(event.getPlayer(), target);
        if (result != MedicResult.SUCCESS) event.getPlayer().sendMessage(Items.color(switch (result) {
            case NOT_MEDIC -> "&cOnly Medics can use this syringe.";
            case INVALID_TARGET -> "&cThat is not a valid syringe target.";
            case NO_KIT_EFFECT -> "&cThat player has no configured kit effect to counter.";
            case COOLDOWN -> "&cYour syringe is on cooldown.";
            default -> "&cYou must hold a valid Medic syringe.";
        }));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { cooldowns.remove(event.getPlayer().getUniqueId()); }

    private KitEffects findActiveKitEffects(Player target) {
        for (String kit : KITS) {
            List<PotionEffectType> active = new ArrayList<>();
            for (String name : plugin.store().config().getStringList("medic.kit-effects." + kit)) {
                XPotion.matchXPotion(name).map(xp -> xp.buildPotionEffect(1, 0).getType())
                        .filter(target::hasPotionEffect).ifPresent(active::add);
            }
            if (!active.isEmpty()) return new KitEffects(kit, active);
        }
        return null;
    }

    private void applyConfiguredDebuffs(Player target) {
        int duration = (int) Math.min(Integer.MAX_VALUE, durationMillis(plugin.store().config().getString("medic.debuffs.duration", "10s"), 10_000L) / 50L);
        int amplifier = Math.max(0, plugin.store().config().getInt("medic.debuffs.amplifier", 0));
        for (String effect : plugin.store().config().getStringList("medic.debuffs.effects")) {
            XPotion.matchXPotion(effect).map(xp -> xp.buildPotionEffect(duration, amplifier)).ifPresent(target::addPotionEffect);
        }
    }

    private BigDecimal reward(String kit) {
        try { return new BigDecimal(plugin.store().config().getString("medic.rewards." + kit, "0")); }
        catch (NumberFormatException exception) { return BigDecimal.ZERO; }
    }

    private void consumeOne(Player player, ItemStack syringe) {
        if (syringe.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else syringe.setAmount(syringe.getAmount() - 1);
    }

    private static long durationMillis(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        String input = value.trim().toLowerCase(java.util.Locale.ROOT);
        char suffix = input.charAt(input.length() - 1);
        long multiplier = 1_000L;
        if (Character.isLetter(suffix)) {
            input = input.substring(0, input.length() - 1).trim();
            multiplier = switch (suffix) {
                case 'd' -> 86_400_000L;
                case 'h' -> 3_600_000L;
                case 'm' -> 60_000L;
                case 's' -> 1_000L;
                default -> -1L;
            };
        }
        if (multiplier < 0L) return fallback;
        try {
            long amount = Long.parseLong(input);
            return amount <= 0L ? fallback : Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException | NumberFormatException exception) { return fallback; }
    }

    private static final class KitEffects {
        private final String kit;
        private final List<PotionEffectType> effects;
        private KitEffects(String kit, List<PotionEffectType> effects) { this.kit = kit; this.effects = effects; }
    }
}