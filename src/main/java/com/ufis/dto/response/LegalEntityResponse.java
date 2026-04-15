package com.ufis.dto.response;

import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;

import java.time.Instant;
import java.util.UUID;

public record LegalEntityResponse(
        UUID id,
        String name,
        LegalEntityType type,
        LegalEntityState state,
        boolean active,
        Instant foundedDate
) {
}
