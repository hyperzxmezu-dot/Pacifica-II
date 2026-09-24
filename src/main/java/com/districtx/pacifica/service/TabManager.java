package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TabManager {
    private static final Pattern HEX_TAG = Pattern.compile("</?#([A-Fa-f0-9]{6})>");
    private final PacificaPlugin plugin;
    private BukkitTask task;
    private Object playerAdapter;

    public TabManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.store().tab().getBoolean("tab.luckperms.enabled", true)
                && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Object luckPerms = providerClass.getMethod("get").invoke(null);
                playerAdapter = luckPerms.getClass().getMethod("getPlayerAdapter", Class.class).invoke(luckPerms, Player.class);
            } catch (Throwable ignored) {
                playerAdapter = null;
            }
        }
    }

    public void start() {
        if (!plugin.store().tab().getBoolean("tab.enabled", true)) return;
        updateAll();
        long period = Math.max(1L, plugin.store().tab().getLong("tab.update-period-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, period, period);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void updateAll() {
        if (!plugin.store().tab().getBoolean("tab.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void update(Player player) {
        if (!player.isOnline()) return;
        int online = Bukkit.getOnlinePlayers().size();
        player.setPlayerListHeaderFooter(lines(player, "tab.header", online), lines(player, "tab.footer", online));
        String prefix = plugin.placeholders().parse(player, plugin.store().tab().getString("tab.fallback-prefix", ""));
        String suffix = plugin.placeholders().parse(player, plugin.store().tab().getString("tab.fallback-suffix", ""));
        if (playerAdapter != null) {
            try {
                Object metadata = playerAdapter.getClass().getMethod("getMetaData", Player.class).invoke(playerAdapter, player);
                Object configuredPrefix = metadata.getClass().getMethod("getPrefix").invoke(metadata);
                Object configuredSuffix = metadata.getClass().getMethod("getSuffix").invoke(metadata);
                if (configuredPrefix != null) prefix = configuredPrefix.toString();
                if (configuredSuffix != null) suffix = configuredSuffix.toString();
            } catch (Throwable ignored) { }
        }
        String format = plugin.store().tab().getString("tab.player-name-format", "%prefix%%name%%suffix%");
        boolean tagsEnabled = plugin.tags() != null
                && plugin.store().tag().getBoolean("tag-system.enabled", true)
                && plugin.store().tag().getBoolean("tag-system.tab.enabled", true);
        String tag = tagsEnabled ? plugin.tags().display(player.getUniqueId()) : "";
        String tagWithSpace = tag.isEmpty() ? "" : tag + " ";
        String name = plugin.placeholders().parse(player, format.replace("%prefix%", prefix).replace("%tag%", tagWithSpace).replace("%TAG%", tagWithSpace).replace("%name%", player.getName()).replace("%suffix%", suffix).replace("%online%", String.valueOf(online)));
        player.setPlayerListName(Items.color(name));
    }

    private String lines(Player player, String path, int online) {
        List<String> configured = plugin.store().tab().getStringList(path);
        if (configured.isEmpty()) return "";
        return color(plugin.placeholders().parse(player, String.join("\n", configured).replace("%online%", String.valueOf(online))));
    }

    private String color(String value) {
        Matcher matcher = HEX_TAG.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            if (tag.startsWith("</")) {
                matcher.appendReplacement(result, "");
                continue;
            }
            StringBuilder replacement = new StringBuilder("§x");
            for (char character : matcher.group(1).toCharArray()) replacement.append('§').append(character);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return Items.color(result.toString());
    }
}