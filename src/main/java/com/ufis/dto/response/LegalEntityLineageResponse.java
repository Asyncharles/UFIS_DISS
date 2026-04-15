package com.ufis.dto.response;

import java.time.Instant;
import java.util.List;

public record LegalEntityLineageResponse(
        LegalEntityResponse legalEntity,
        List<LegalEntityLineageEntryResponse> issuerLineage,
        NameHistoryResponse nameHistory,
        Instant resolvedAt
) {
}
