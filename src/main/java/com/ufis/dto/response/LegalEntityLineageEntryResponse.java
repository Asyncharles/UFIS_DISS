package com.ufis.dto.response;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;

import java.time.Instant;
import java.util.UUID;

public record LegalEntityLineageEntryResponse(
        UUID parentId,
        String parentName,
        LegalEntityType parentType,
        LegalEntityState parentState,
        boolean parentActive,
        Instant parentFoundedDate,
        UUID actionId,
        CorporateActionType actionType,
        Instant actionDate
) {
}
