package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatListener implements Listener {
    private final PacificaPlugin plugin;
    public ChatListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void chat(AsyncPlayerChatEvent event) {
        if (!plugin.store().chat().getBoolean("chat.enabled", true)) return;
        event.setMessage(plugin.chats().colorMessage(event.getPlayer(), event.getMessage()));
        String rendered = plugin.chats().formatMessage(event.getPlayer(), event.getMessage());
        event.setFormat(rendered.replace("%", "%%"));
    }
}