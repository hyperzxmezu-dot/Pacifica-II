package com.districtx.pacifica.api.crew;

import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;

public interface CrewAPI {
    Optional<Crew> getCrewForPlayer(UUID playerId);
    Optional<Crew> getCrewById(UUID crewId);
    Optional<Crew> getCrewByName(String name);
    Optional<CrewMember> getMember(UUID playerId);
    int getCrewLevel(UUID playerId);
    long getCrewExperience(UUID playerId);
    long getStreetCred(UUID playerId);
    int getPerkLevel(UUID playerId, String perkId);
    CompletableFuture<CrewResult> awardCrewExperience(UUID playerId, long amount);
}