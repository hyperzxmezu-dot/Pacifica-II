package com.districtx.pacifica.api.jobs;

import java.math.BigDecimal;
import org.bukkit.entity.Player;

/** Public API for Cop arrests and prisoner bribes. */
public interface ArrestAPI {
    /** Attempts to arrest an eligible player using the specified method. */
    ArrestResult attemptArrest(Player cop, Player target, ArrestMethod method);

    /** Creates an offer for the Cop responsible for the prisoner's active sentence. */
    BribeResult offerBribe(Player prisoner, BigDecimal amount);

    /** Accepts the named prisoner's active offer, if this Cop is responsible. */
    BribeResult acceptBribe(Player cop, String prisonerName);

    /** Denies the named prisoner's active offer, if this Cop is responsible. */
    BribeResult denyBribe(Player cop, String prisonerName);
}