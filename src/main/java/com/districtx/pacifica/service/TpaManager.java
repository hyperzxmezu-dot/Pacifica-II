package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TpaManager {
    private final PacificaPlugin plugin;
    private final Map<UUID, Request> requests = new HashMap<>();
    public TpaManager(PacificaPlugin plugin) { this.plugin = plugin; }

    public void send(Player requester, Player target) {
        if (!requester.isOnline() || !target.isOnline() || requester.getUniqueId().equals(target.getUniqueId())) return;
        cancelOutgoing(requester, false);
        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Request request = requests.get(requester.getUniqueId());
            if (request == null || !request.target().equals(target.getUniqueId())) return;
            requests.remove(requester.getUniqueId());
            if (requester.isOnline()) requester.sendMessage(plugin.store().message(requester, "tpa.expired-requester"));
            if (target.isOnline()) target.sendMessage(plugin.store().message(target, "tpa.expired-target", "%player%", requester.getName()));
        }, 600L);
        requests.put(requester.getUniqueId(), new Request(requester.getUniqueId(), target.getUniqueId(), expiry));
        requester.spigot().sendMessage(messageWithButton("tpa.request-sent", target.getName(), "tpa.cancel-button", "/tpcancel", "Click to cancel"));
        target.spigot().sendMessage(messageWithButtons("tpa.request-received", requester.getName(), "tpa.accept-button", "/tpaccept", "Click to accept", "tpa.deny-button", "/tpdeny", "Click to deny"));
    }

    public void accept(Player target) {
        Request request = findForTarget(target.getUniqueId());
        if (request == null) { target.sendMessage(plugin.store().message(target, "tpa.no-pending")); return; }
        remove(request);
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(plugin.store().message(target, "tpa.requester-offline"));
            return;
        }
        plugin.teleports().start(requester, target.getLocation(), true, TeleportManager.TeleportContext.PLAYER_TPA, Collections.singletonMap("%player%", target.getName()));
        target.sendMessage(plugin.store().message(target, "tpa.accepted"));
    }

    public void deny(Player target) {
        Request request = findForTarget(target.getUniqueId());
        if (request == null) { target.sendMessage(plugin.store().message(target, "tpa.no-pending")); return; }
        remove(request);
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) requester.sendMessage(plugin.store().message(requester, "tpa.denied-requester"));
        target.sendMessage(plugin.store().message(target, "tpa.denied"));
    }

    public void cancelOutgoing(Player requester, boolean notify) {
        Request request = requests.remove(requester.getUniqueId());
        if (request != null) {
            request.expiry().cancel();
            Player target = Bukkit.getPlayer(request.target());
            if (target != null && target.isOnline()) target.sendMessage(plugin.store().message(target, "tpa.cancelled-target", "%player%", requester.getName()));
            if (notify) requester.sendMessage(plugin.store().message(requester, "tpa.cancelled"));
        }
    }

    public void playerQuit(Player player) {
        cancelOutgoing(player, false);
        Request incoming = findForTarget(player.getUniqueId());
        if (incoming != null) {
            remove(incoming);
            Player requester = Bukkit.getPlayer(incoming.requester());
            if (requester != null) requester.sendMessage(plugin.store().message(requester, "tpa.target-disconnected"));
        }
    }

    private Request findForTarget(UUID target) { return requests.values().stream().filter(request -> request.target().equals(target)).findFirst().orElse(null); }
    private void remove(Request request) { requests.remove(request.requester()); request.expiry().cancel(); }
    public void clear() { requests.values().forEach(request -> request.expiry().cancel()); requests.clear(); }
    private BaseComponent[] messageWithButton(String messagePath, String playerName, String buttonPath, String command, String hover) {
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(plugin.store().message(messagePath, "%player%", playerName)));
        message.addExtra(button(buttonPath, command, hover));
        return new BaseComponent[]{message};
    }
    private BaseComponent[] messageWithButtons(String messagePath, String playerName, String firstPath, String firstCommand, String firstHover, String secondPath, String secondCommand, String secondHover) {
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(plugin.store().message(messagePath, "%player%", playerName)));
        message.addExtra(" ");
        message.addExtra(button(firstPath, firstCommand, firstHover));
        message.addExtra(" ");
        message.addExtra(button(secondPath, secondCommand, secondHover));
        return new BaseComponent[]{message};
    }
    private TextComponent button(String path, String command, String hover) {
        TextComponent button = new TextComponent(TextComponent.fromLegacyText(plugin.store().message(path)));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(Items.color("&7" + hover))));
        return button;
    }
    private record Request(UUID requester, UUID target, BukkitTask expiry) { }
}