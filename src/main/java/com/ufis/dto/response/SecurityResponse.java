package com.ufis.dto.response;

import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;

import java.time.Instant;
import java.util.UUID;

public record SecurityResponse(
        UUID id,
        String name,
        String isin,
        SecurityType type,
        SecurityState state,
        boolean active,
        Instant issueDate,
        Instant maturityDate,
        IssuerRefResponse issuer
) {
}
