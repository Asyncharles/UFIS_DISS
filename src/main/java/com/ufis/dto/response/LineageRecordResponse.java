package com.ufis.dto.response;

import java.util.UUID;

public record LineageRecordResponse(
        UUID id,
        UUID actionId,
        UUID parentSecurityId,
        UUID childSecurityId,
        UUID parentEntityId,
        UUID childEntityId
) {
}
