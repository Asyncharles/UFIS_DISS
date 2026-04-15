package com.ufis.dto.response;

import com.ufis.domain.enums.SecurityRole;

public record SecurityActionListEntryResponse(
        CorporateActionRecordResponse action,
        SecurityRole role
) {
}
