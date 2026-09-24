package com.districtx.pacifica.service;

import org.bukkit.entity.Player;

public interface ChatManager {
    String formatMessage(Player player, String message);
    String getFormat(Player player);
    String getPrefix(Player player);
    String getTag(Player player);
    String getPlayerName(Player player);
    String getNickname(Player player);
    String getSuffix(Player player);
}