package com.ufis.dto.response;

import java.util.List;

public record SearchResponse(
        List<SearchSecurityResultResponse> securities,
        List<SearchLegalEntityResultResponse> legalEntities,
        List<SearchCorporateActionResultResponse> corporateActions
) {
}
