package com.ufis.dto.response;

import java.time.Instant;
import java.util.List;

public record SecurityLineageResponse(
        SecurityResponse security,
        List<SecurityLineageEntryResponse> securityLineage,
        LegalEntityResponse currentIssuer,
        List<LegalEntityLineageEntryResponse> issuerLineage,
        NameHistoryResponse nameHistory,
        Instant resolvedAt
) {
}
