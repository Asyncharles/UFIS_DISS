package com.ufis.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SecurityNameHistoryEntryResponse(
        Instant validDate,
        String previousName,
        String newName,
        String previousIsin,
        String newIsin,
        UUID actionId
) {
}
