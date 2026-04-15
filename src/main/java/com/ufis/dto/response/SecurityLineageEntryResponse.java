package com.ufis.dto.response;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;

import java.time.Instant;
import java.util.UUID;

public record SecurityLineageEntryResponse(
        UUID parentId,
        String parentName,
        String parentIsin,
        SecurityType parentType,
        SecurityState parentState,
        boolean parentActive,
        Instant parentIssueDate,
        Instant parentMaturityDate,
        IssuerRefResponse issuer,
        UUID actionId,
        CorporateActionType actionType,
        Instant actionDate
) {
}
