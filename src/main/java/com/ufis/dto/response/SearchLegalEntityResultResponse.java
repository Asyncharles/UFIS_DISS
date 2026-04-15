package com.ufis.dto.response;

import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;

import java.util.UUID;

public record SearchLegalEntityResultResponse(
        UUID id,
        String name,
        LegalEntityType type,
        LegalEntityState state,
        boolean active
) {
}
