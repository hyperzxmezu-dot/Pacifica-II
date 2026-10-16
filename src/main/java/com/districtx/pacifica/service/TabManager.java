package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    /** Resolves the player's LuckPerms prefix, falling back to the configured tab prefix. */
    public String prefix(Player player) {
        return prefixSuffix(player).prefix();
    }

    public void updateAll() {
        if (!plugin.store().tab().getBoolean("tab.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void update(Player player) {
        if (!player.isOnline()) return;
        int online = Bukkit.getOnlinePlayers().size();
        player.setPlayerListHeaderFooter(lines(player, "tab.header", online), lines(player, "tab.footer", online));
        PrefixSuffix prefixSuffix = prefixSuffix(player);
        String prefix = prefixSuffix.prefix();
        String suffix = prefixSuffix.suffix();
        String format = plugin.store().tab().getString("tab.player-name-format", "%prefix% %name%%suffix%");
        boolean tagsEnabled = plugin.tags() != null
                && plugin.store().tag().getBoolean("tag-system.enabled", true)
                && plugin.store().tag().getBoolean("tag-system.tab.enabled", true);
        String tag = plugin.identity().display(player, tagsEnabled);
        String tagWithSpace = tag.isEmpty() ? "" : tag + " ";
        String playerName = getFormattedPlayerName(player);
        String name = plugin.placeholders().parse(player, format.replace("%prefix%", prefix).replace("%tag%", tagWithSpace).replace("%TAG%", tagWithSpace).replace("%name%", playerName).replace("%suffix%", suffix).replace("%online%", String.valueOf(online)));
        player.setPlayerListName(Items.color(name));
    }

    /** Returns the player's name with the effective LuckPerms prefix formatting, or the unchanged name if none applies. */
    public String getFormattedPlayerName(Player player) {
        if (player == null) return "";
        String style = playerNameStyle(player);
        return style.isEmpty() ? player.getName() : Items.color(style + player.getName() + "&r");
    }

    String playerNameStyle(Player player) {
        String prefix = prefix(player);
        ChatColor color = null;
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i < prefix.length(); i++) {
            char marker = prefix.charAt(i);
            if ((marker != '&' && marker != ChatColor.COLOR_CHAR) || i + 1 >= prefix.length()) continue;
            char code = Character.toLowerCase(prefix.charAt(++i));
            if (isLegacyColor(code)) {
                color = ChatColor.getByChar(code);
                formats.setLength(0);
            } else if (code == 'r') {
                color = null;
                formats.setLength(0);
            } else if (isLegacyFormat(code) && formats.indexOf(String.valueOf(code)) < 0) {
                formats.append(code);
            }
        }
        if (color == null && formats.isEmpty()) return "";
        StringBuilder style = new StringBuilder("&r");
        if (color != null) style.append('&').append(color.getChar());
        for (int i = 0; i < formats.length(); i++) style.append('&').append(formats.charAt(i));
        return style.toString();
    }

    ChatColor playerNameColor(Player player) {
        String prefix = prefix(player);
        ChatColor color = null;
        for (int i = 0; i < prefix.length(); i++) {
            char marker = prefix.charAt(i);
            if ((marker != '&' && marker != ChatColor.COLOR_CHAR) || i + 1 >= prefix.length()) continue;
            char code = Character.toLowerCase(prefix.charAt(++i));
            if (isLegacyColor(code)) color = ChatColor.getByChar(code);
            else if (code == 'r') color = null;
        }
        return color;
    }

    private boolean isLegacyColor(char code) {
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f';
    }

    private boolean isLegacyFormat(char code) {
        return code >= 'k' && code <= 'o';
    }

    private String lines(Player player, String path, int online) {
        List<String> configured = plugin.store().tab().getStringList(path);
        if (configured.isEmpty()) return "";
        return color(plugin.placeholders().parse(player, String.join("\n", configured).replace("%online%", String.valueOf(online))));
    }

    private PrefixSuffix prefixSuffix(Player player) {
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
        return new PrefixSuffix(prefix, suffix);
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

    private record PrefixSuffix(String prefix, String suffix) { }
}