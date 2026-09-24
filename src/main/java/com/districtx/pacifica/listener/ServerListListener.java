package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public final class ServerListListener implements Listener {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final PacificaPlugin plugin;

    public ServerListListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void ping(ServerListPingEvent event) {
        if (!plugin.store().motd().getBoolean("motd.server-list.enabled", true)) return;
        String configuredFirst = plugin.store().motd().getString("motd.Server-MOTD.Line-1");
        String configuredSecond = plugin.store().motd().getString("motd.Server-MOTD.Line-2");
        List<String> lines = plugin.store().motd().getStringList("motd.server-list.motd");
        String first = configuredFirst != null ? color(configuredFirst) : lines.size() > 0 ? color(lines.get(0)) : "";
        String second = configuredSecond != null ? color(configuredSecond) : lines.size() > 1 ? color(lines.get(1)) : "";
        event.setMotd(first + "\n" + second);
        event.setMaxPlayers(Math.max(1, Bukkit.getOnlinePlayers().size() + 1));
    }

    private String color(String value) {
        Matcher matcher = HEX.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char character : matcher.group(1).toCharArray()) replacement.append('§').append(character);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return ChatColor.translateAlternateColorCodes('&', result.toString());
    }
}