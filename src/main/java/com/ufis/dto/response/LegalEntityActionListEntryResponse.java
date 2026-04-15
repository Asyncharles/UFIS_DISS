package com.ufis.dto.response;

import com.ufis.domain.enums.LegalEntityRole;

public record LegalEntityActionListEntryResponse(
        CorporateActionRecordResponse action,
        LegalEntityRole role
) {
}
