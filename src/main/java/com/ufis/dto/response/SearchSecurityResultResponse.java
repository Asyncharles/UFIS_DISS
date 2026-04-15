package com.ufis.dto.response;

import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;

import java.util.UUID;

public record SearchSecurityResultResponse(
        UUID id,
        String name,
        String isin,
        SecurityType type,
        SecurityState state,
        boolean active,
        UUID issuerId,
        String issuerName
) {
}
