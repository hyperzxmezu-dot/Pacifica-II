package com.districtx.pacifica;

import com.districtx.pacifica.api.combat.CombatReason;
import com.districtx.pacifica.api.combat.CombatSession;
import com.districtx.pacifica.api.combat.CombatTagAPI;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Internal service adapter registered for Pacifica-II API consumers. */
final class PacificaCombatTagService implements CombatTagAPI {
    private final PacificaPlugin plugin;

    /** @param plugin owning Pacifica-II plugin instance */
    PacificaCombatTagService(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override public boolean isAvailable() { return plugin.isEnabled(); }

    @Override public boolean isInCombat(Player player) {
        return isAvailable() && plugin.combatTags().isTagged(player);
    }

    @Override public Optional<CombatSession> getCombatSession(Player player) {
        if (!isAvailable()) return Optional.empty();
        return plugin.combatTags().session(player);
    }

    @Override public int getRemainingSeconds(Player player) {
        return isAvailable() ? plugin.combatTags().remaining(player) : 0;
    }

    @Override public void tag(Player player) {
        if (isAvailable() && player != null && player.isOnline()) plugin.combatTags().tag(player);
    }

    @Override public void tag(Player player, Player opponent) {
        if (isAvailable() && player != null && opponent != null && player.isOnline() && opponent.isOnline()) {
            plugin.combatTags().tag(player, opponent);
        }
    }

    @Override public void untag(Player player) {
        if (isAvailable() && player != null) plugin.combatTags().clear(player, CombatReason.API);
    }
}