package com.ufis.dto.response;

import java.util.List;

public record CorporateActionDetailResponse(
        CorporateActionRecordResponse action,
        List<SecurityResponse> sourceSecurities,
        List<SecurityResponse> resultSecurities,
        List<SecurityResponse> terminatedSecurities,
        List<LegalEntityResponse> sourceEntities,
        List<LegalEntityResponse> resultEntities,
        List<LineageRecordResponse> lineageRecords
) {
}
