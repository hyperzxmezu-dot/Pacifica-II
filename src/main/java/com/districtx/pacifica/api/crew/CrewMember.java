package com.districtx.pacifica.api.crew;

import java.util.UUID;

public record CrewMember(UUID playerId, CrewRank rank, long joinedAt,
                         long contributedXp, long contributedStreetCred,
                         double bankContributions) {
}