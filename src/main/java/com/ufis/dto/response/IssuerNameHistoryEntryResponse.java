package com.ufis.dto.response;

import java.time.Instant;
import java.util.UUID;

public record IssuerNameHistoryEntryResponse(
        Instant validDate,
        String previousName,
        String newName,
        UUID actionId
) {
}
