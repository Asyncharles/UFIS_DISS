package com.ufis.service.lineage;

import com.ufis.dto.response.LegalEntityLineageEntryResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.NameHistoryResponse;
import com.ufis.dto.response.SecurityLineageEntryResponse;
import com.ufis.dto.response.SecurityLineageResponse;
import com.ufis.dto.response.SecurityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityLineageService {
    private final LineageResolutionSupport lineageResolutionSupport;

    @SuppressWarnings("unchecked")
    public SecurityLineageResponse getLineage(UUID securityId, Instant validAt) {
        SecurityResponse security = lineageResolutionSupport.resolveSecurityResponse(securityId, validAt);
        List<SecurityLineageEntryResponse> securityLineage = lineageResolutionSupport.resolveSecurityAncestors(securityId, validAt);

        UUID currentIssuerId = null;

        if (security.issuer() != null) {
            currentIssuerId = security.issuer().id();
        }

        LegalEntityResponse currentIssuer = currentIssuerId == null
                ? null
                : lineageResolutionSupport.resolveLegalEntityResponse(currentIssuerId, validAt);
        List<LegalEntityLineageEntryResponse> issuerLineage = currentIssuerId == null
                ? List.of()
                : lineageResolutionSupport.resolveLegalEntityAncestors(currentIssuerId, validAt);

        NameHistoryResponse nameHistory = lineageResolutionSupport.buildSecurityNameHistory(securityId, securityLineage, currentIssuerId, issuerLineage, validAt);

        return new SecurityLineageResponse(security, securityLineage, currentIssuer, issuerLineage, nameHistory, validAt);
    }
}
