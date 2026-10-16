package com.districtx.pacifica.api.jobs;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Public API for the Medic kit and syringe ability. */
public interface MedicAPI {
    /** Creates a PDC-identified Medic syringe. */
    ItemStack createSyringe();

    /** Checks the syringe's persistent identifier rather than its display name. */
    boolean isSyringe(ItemStack item);

    /** Uses a held syringe against a valid target. */
    MedicResult useSyringe(Player medic, Player target);

    /** Lists online Medic players carrying a valid syringe. */
    List<Player> getActiveMedicKitPlayers();
}