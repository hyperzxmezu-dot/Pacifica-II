package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;

public final class EconomyManager {
    private final PacificaPlugin plugin;
    private boolean unavailableWarningLogged;

    public EconomyManager(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        boolean available = coreEconomyService() != null || vaultEconomy() != null;
        if (!available && !unavailableWarningLogged) {
            unavailableWarningLogged = true;
            plugin.getLogger().warning("[Trashcan] Economy provider unavailable.");
        }
        return available;
    }

    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        if (player == null || player.getUniqueId() == null || amount == null || amount.signum() <= 0) return false;
        Object coreEconomy = coreEconomyService();
        if (coreEconomy != null) {
            try {
                Object result = coreEconomy.getClass().getMethod("deposit", java.util.UUID.class, BigDecimal.class)
                        .invoke(coreEconomy, player.getUniqueId(), amount);
                if (Boolean.TRUE.equals(result)) return true;
                plugin.getLogger().warning("[Trashcan] Pacifica-Core deposit failed for player " + player.getUniqueId() + ".");
                return false;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                plugin.getLogger().warning("[Trashcan] Pacifica-Core deposit failed for player " + player.getUniqueId() + ".");
                return false;
            }
        }
        Economy economy = vaultEconomy();
        double value = amount.doubleValue();
        if (economy == null || !Double.isFinite(value)) return false;
        EconomyResponse response;
        try {
            response = economy.depositPlayer(player, value);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[Economy] Vault deposit failed for player " + player.getUniqueId() + ".");
            return false;
        }
        if (response == null || !response.transactionSuccess()) {
            plugin.getLogger().warning("[Economy] Vault deposit failed for player " + player.getUniqueId() + ".");
            return false;
        }
        return true;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return Double.isFinite(amount) && deposit(player, BigDecimal.valueOf(amount));
    }

    public boolean transfer(Player recipient, Player victim, double percentage) {
        if (recipient == null || victim == null || percentage <= 0 || !Double.isFinite(percentage)) return false;
        BigDecimal amount = balance(victim).multiply(BigDecimal.valueOf(Math.min(1.0, percentage)));
        return transfer(victim, recipient, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return Double.isFinite(amount) && withdraw(player, BigDecimal.valueOf(amount));
    }

    public BigDecimal balance(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) return BigDecimal.ZERO;
        Object coreEconomy = coreEconomyService();
        if (coreEconomy != null) {
            try {
                Object result = coreEconomy.getClass().getMethod("getBalance", java.util.UUID.class)
                        .invoke(coreEconomy, player.getUniqueId());
                if (result instanceof BigDecimal amount && amount.signum() >= 0) return amount;
                return BigDecimal.ZERO;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                return BigDecimal.ZERO;
            }
        }
        Economy economy = vaultEconomy();
        if (economy == null) return BigDecimal.ZERO;
        try {
            double balance = economy.getBalance(player);
            return Double.isFinite(balance) && balance > 0 ? BigDecimal.valueOf(balance) : BigDecimal.ZERO;
        } catch (RuntimeException exception) {
            return BigDecimal.ZERO;
        }
    }

    public boolean hasBalance(OfflinePlayer player, BigDecimal amount) {
        return amount != null && amount.signum() >= 0 && balance(player).compareTo(amount) >= 0;
    }

    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        if (player == null || player.getUniqueId() == null || amount == null || amount.signum() <= 0) return false;
        Object coreEconomy = coreEconomyService();
        if (coreEconomy != null) {
            try {
                Object result = coreEconomy.getClass().getMethod("withdraw", java.util.UUID.class, BigDecimal.class)
                        .invoke(coreEconomy, player.getUniqueId(), amount);
                return Boolean.TRUE.equals(result);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                return false;
            }
        }
        Economy economy = vaultEconomy();
        double value = amount.doubleValue();
        if (economy == null || !Double.isFinite(value)) return false;
        try {
            if (economy.getBalance(player) < value) return false;
            EconomyResponse response = economy.withdrawPlayer(player, value);
            return response != null && response.transactionSuccess();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public synchronized boolean transfer(OfflinePlayer from, OfflinePlayer to, BigDecimal amount) {
        if (from == null || to == null || from.getUniqueId() == null || to.getUniqueId() == null
                || from.getUniqueId().equals(to.getUniqueId()) || amount == null || amount.signum() <= 0
                || !hasBalance(from, amount) || !withdraw(from, amount)) return false;
        if (deposit(to, amount)) return true;
        if (!deposit(from, amount)) plugin.getLogger().severe("Could not roll back failed economy transfer from " + from.getUniqueId() + ".");
        return false;
    }

    private Economy vaultEconomy() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    private Object coreEconomyService() {
        Plugin core = Bukkit.getPluginManager().getPlugin("Pacifica-Core");
        if (core == null || !core.isEnabled()) return null;
        try {
            Class<?> coreClass = core.getClass().getClassLoader().loadClass("com.districtx.pacificacore.PacificaCore");
            Object api = coreClass.getMethod("getAPI").invoke(null);
            return api == null ? null : api.getClass().getMethod("getEconomyService").invoke(api);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }
}