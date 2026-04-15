package com.ufis.dto.response;

import java.util.UUID;

public record IssuerUpdateResponse(
        UUID securityId,
        UUID oldIssuerId,
        UUID newIssuerId
) {
}
