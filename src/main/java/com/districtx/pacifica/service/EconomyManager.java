package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyManager {
    private final Economy economy;

    public EconomyManager(PacificaPlugin plugin) {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = provider == null ? null : provider.getProvider();
        if (economy == null) plugin.getLogger().warning("No Vault economy provider was found; selling is disabled.");
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0) return false;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0 || economy.getBalance(player) < amount) return false;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    public boolean transfer(Player recipient, Player victim, double percentage) {
        if (economy == null || percentage <= 0) return false;
        double amount = economy.getBalance(victim) * Math.min(1.0, percentage);
        if (amount <= 0 || !withdraw(victim, amount)) return false;
        if (deposit(recipient, amount)) return true;
        deposit(victim, amount);
        return false;
    }
}