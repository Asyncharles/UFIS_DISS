package com.ufis.dto.response;

import com.ufis.domain.enums.CorporateActionType;

import java.time.Instant;
import java.util.UUID;

public record CorporateActionRecordResponse(
        UUID id,
        CorporateActionType type,
        Instant validDate,
        String description,
        String splitRatio
) {
}
