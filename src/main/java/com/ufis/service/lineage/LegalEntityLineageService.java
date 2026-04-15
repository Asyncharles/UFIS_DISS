package com.ufis.service.lineage;

import com.ufis.dto.response.LegalEntityLineageEntryResponse;
import com.ufis.dto.response.LegalEntityLineageResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.NameHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegalEntityLineageService {
    private final LineageResolutionSupport lineageResolutionSupport;

    public LegalEntityLineageResponse getLineage(UUID entityId, Instant validAt) {
        LegalEntityResponse legalEntity = lineageResolutionSupport.resolveLegalEntityResponse(entityId, validAt);
        List<LegalEntityLineageEntryResponse> issuerLineage = lineageResolutionSupport.resolveLegalEntityAncestors(entityId, validAt);
        NameHistoryResponse nameHistory = lineageResolutionSupport.buildLegalEntityNameHistory(entityId, issuerLineage, validAt);

        return new LegalEntityLineageResponse(legalEntity, issuerLineage, nameHistory, validAt);
    }
}
