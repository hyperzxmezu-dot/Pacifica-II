package com.districtx.pacifica.api.crew;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record Crew(UUID id, String name, long createdAt, UUID leaderId, long crewXp,
                   int level, long streetCred, BigDecimal bankBalance, int memberLimit,
                   Map<UUID, CrewMember> members) {
    public Crew {
        members = Map.copyOf(members);
    }
}