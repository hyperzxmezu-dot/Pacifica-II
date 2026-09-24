package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ChatService implements ChatManager {
    private final PacificaPlugin plugin;
    private Object playerAdapter;
    private Method metadataMethod;
    private Method prefixMethod;
    private Method suffixMethod;

    public ChatService(PacificaPlugin plugin) {
        this.plugin = plugin;
        initializeLuckPerms();
    }

    @Override public String formatMessage(Player player, String message) {
        String format = getFormat(player);
        format = replaceOptional(format, "%luckperms_prefix%", getPrefix(player));
        format = replaceOptional(format, "%tag%", getTag(player));
        format = format
                .replace("%player_name%", getPlayerName(player))
                .replace("%player_displayname%", getNickname(player))
                .replace("%message%", message == null ? "" : message);
        format = replaceOptional(format, "%luckperms_suffix%", getSuffix(player));
        return Items.color(plugin.placeholders().parse(player, format));
    }

    @Override public String getFormat(Player player) {
        return plugin.store().chat().getString("chat.format", "%luckperms_prefix% %tag% %player_displayname% &f%message%");
    }

    @Override public String getPrefix(Player player) { return metadata(player, "getPrefix"); }

    @Override public String getTag(Player player) {
        if (!plugin.store().chat().getBoolean("chat.integrations.tags", true) || plugin.tags() == null) return "";
        return plugin.tags().display(player.getUniqueId());
    }

    @Override public String getPlayerName(Player player) { return player.getName(); }

    @Override public String getNickname(Player player) {
        String display = player.getDisplayName();
        display = display == null || display.trim().isEmpty() ? player.getName() : display;
        String prefix = getPrefix(player);
        if (prefix.trim().isEmpty()) return display;
        return prefixStyle(prefix) + ChatColor.stripColor(Items.color(display));
    }

    @Override public String getSuffix(Player player) { return metadata(player, "getSuffix"); }

    public String colorMessage(Player player, String message) {
        if (message == null) return "";
        boolean colors = plugin.store().chat().getBoolean("chat.colors.enabled", true)
                && player.hasPermission("pacifica.chat.color");
        boolean formatting = plugin.store().chat().getBoolean("chat.colors.formatting", true)
                && player.hasPermission("pacifica.chat.format");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if (current == '&' && i + 1 < message.length()) {
                char code = Character.toLowerCase(message.charAt(i + 1));
                boolean color = code >= '0' && code <= '9' || code >= 'a' && code <= 'f';
                boolean format = code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o' || code == 'r';
                if (color && colors || format && formatting) {
                    result.append('&').append(code);
                    i++;
                    continue;
                }
                if (color || format) { i++; continue; }
            }
            result.append(current);
        }
        return colors || formatting ? Items.color(result.toString()) : ChatColor.stripColor(result.toString());
    }

    private String replaceOptional(String format, String placeholder, String value) {
        if (value != null && !value.trim().isEmpty()) return format.replace(placeholder, value);
        return format.replace(placeholder + " ", "").replace(" " + placeholder, "").replace(placeholder, "");
    }

    private String metadata(Player player, String methodName) {
        if (playerAdapter == null) return "";
        try {
            Object metadata = metadataMethod.invoke(playerAdapter, player);
            if (metadata == null) return "";
            Method method = "getPrefix".equals(methodName) ? prefixMethod : suffixMethod;
            Object value = method.invoke(metadata);
            return value == null ? "" : value.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void initializeLuckPerms() {
        if (!plugin.store().chat().getBoolean("chat.integrations.luckperms", true)
                || !Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return;
        try {
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            Object luckPerms = null;
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(luckPermsClass);
            if (registration != null) {
                luckPerms = registration.getProvider();
            }
            if (luckPerms == null) {
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
                luckPerms = provider.getMethod("get").invoke(null);
            }
            if (luckPerms != null) {
                Method playerAdapterMethod = luckPermsClass.getMethod("getPlayerAdapter", Class.class);
                playerAdapter = playerAdapterMethod
                        .invoke(luckPerms, Player.class);
                Class<?> adapterClass = Class.forName("net.luckperms.api.platform.PlayerAdapter");
                metadataMethod = adapterClass.getMethod("getMetaData", Object.class);
                Class<?> metadataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
                prefixMethod = metadataClass.getMethod("getPrefix");
                suffixMethod = metadataClass.getMethod("getSuffix");
            }
        } catch (Throwable ignored) { }
    }

    private String prefixStyle(String prefix) {
        StringBuilder style = new StringBuilder();
        for (int i = 0; i < prefix.length() - 1; i++) {
            char marker = prefix.charAt(i);
            if (marker != '&' && marker != ChatColor.COLOR_CHAR) continue;
            char code = Character.toLowerCase(prefix.charAt(++i));
            if (isLegacyStyle(code)) {
                style.append('&').append(code);
            }
        }
        return style.toString();
    }

    private boolean isLegacyStyle(char code) {
        return code >= '0' && code <= '9'
                || code >= 'a' && code <= 'f'
                || code >= 'k' && code <= 'o'
                || code == 'r';
    }
}