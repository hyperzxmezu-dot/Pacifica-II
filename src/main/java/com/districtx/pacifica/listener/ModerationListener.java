package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class ModerationListener implements Listener {
    private final PacificaPlugin plugin;

    public ModerationListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void chat(AsyncPlayerChatEvent event) {
        String message = ChatColor.stripColor(event.getMessage()).toLowerCase(Locale.ROOT);
        String compact = message.replaceAll("[^a-z0-9.:/-]", "");
        boolean repeated = plugin.store().moderation().getBoolean("moderation.anti-chat.block-repeated-messages", true) && message.equalsIgnoreCase(last(event.getPlayer()));
        boolean blocked = repeated || matches(message, compact, plugin.store().moderation().getStringList("moderation.anti-chat.blocked-patterns"));
        if (!blocked) {
            plugin.store().setLastChat(event.getPlayer(), message);
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.store().message(event.getPlayer(), "anti-chat.blocked"));
    }

    private String last(Player player) { return plugin.store().lastChat(player); }

    private boolean matches(String message, String compact, List<String> patterns) {
        for (String expression : patterns) {
            try {
                if (Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(message).find() || Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(compact).find()) return true;
            } catch (RuntimeException ignored) { }
        }
        return compact.matches(".*(?:[a-z0-9-]+\\.)+(?:com|net|org|gg|io|me)(?:/.*)?") || compact.matches(".*\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?.*");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().split(" ", 2)[0].toLowerCase(Locale.ROOT).substring(1);
        String label = command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;
        if (label.equals("help")) {
            event.setCancelled(true);
            int page = 1;
            String[] parts = event.getMessage().trim().split("\\s+");
            if (parts.length > 1) try { page = Math.max(1, Integer.parseInt(parts[1])); } catch (NumberFormatException ignored) { }
            List<String> lines = plugin.store().moderation().getStringList("moderation.help.pages." + page);
            if (lines.isEmpty()) lines = plugin.store().moderation().getStringList("moderation.help.pages.1");
            Player player = event.getPlayer();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.store().moderation().getString("moderation.help.header", "&6&lHelp")));
            lines.forEach(line -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', line)));
            return;
        }
        if (!label.equals("pl") && !label.equals("plugins")) return;
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("pacifica.pluginlist")) {
            event.setCancelled(true);
            player.sendMessage(plugin.store().message(player, "plugin-list.hidden"));
        }
    }
}