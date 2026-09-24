package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlaceholderService {
    private final PacificaPlugin plugin;
    private final boolean available;
    private final Method setPlaceholders;

    public PlaceholderService(PacificaPlugin plugin) {
        this.plugin = plugin;
        Method method = null;
        boolean found = false;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                method = api.getMethod("setPlaceholders", Player.class, String.class);
                found = true;
            } catch (ReflectiveOperationException ignored) { }
        }
        available = found;
        setPlaceholders = method;
    }

    public boolean isAvailable() { return available; }

    public String parse(Player player, String text) {
        if (text == null) return "";
        String result = text;
        String tag = player == null || plugin.tags() == null ? "" : plugin.tags().display(player.getUniqueId());
        result = result.replace("%TAG%", tag).replace("%tag%", tag);
        result = result.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        if (player != null) {
            result = result.replace("%name%", player.getName()).replace("%player_name%", player.getName());
        }
        if (available && player != null) {
            try { result = (String) setPlaceholders.invoke(null, player, result); }
            catch (ReflectiveOperationException ignored) { }
        }
        return result;
    }
}