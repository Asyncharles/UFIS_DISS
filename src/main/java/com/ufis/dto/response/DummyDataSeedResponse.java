package com.ufis.dto.response;

import com.ufis.simulator.DataSimulator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DummyDataSeedResponse(
        DataSimulator.Tier tier,
        int initialEntities,
        int initialSecurities,
        int corporateActions,
        UUID sampleEntityId,
        UUID sampleSecurityId,
        UUID sampleActionId,
        String searchHint,
        List<Instant> auditHintTimestamps
) {
}
