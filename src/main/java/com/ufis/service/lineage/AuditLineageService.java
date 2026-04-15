package com.ufis.service.lineage;

import com.ufis.dto.response.LegalEntityLineageEntryResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.NameHistoryResponse;
import com.ufis.dto.response.SecurityLineageEntryResponse;
import com.ufis.dto.response.SecurityLineageResponse;
import com.ufis.dto.response.SecurityResponse;
import datomic.Connection;
import datomic.Database;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLineageService {
    private final Connection connection;
    private final LineageResolutionSupport lineageResolutionSupport;

    public SecurityLineageResponse getSecurityAuditLineage(UUID securityId, Instant validAt, Instant knownAt) {
        Database latestDb = connection.db();
        Database asOfDb = latestDb.asOf(Date.from(knownAt));
        Database asOfHistoryDb = latestDb.history().asOf(Date.from(knownAt));

        SecurityResponse security = lineageResolutionSupport.resolveSecurityResponse(asOfDb, asOfHistoryDb, securityId, validAt);
        List<SecurityLineageEntryResponse> securityLineage = lineageResolutionSupport.resolveSecurityAncestors(asOfDb, asOfHistoryDb, securityId, validAt);

        UUID currentIssuerId = security.issuer() == null ? null : security.issuer().id();
        LegalEntityResponse currentIssuer = currentIssuerId == null
                ? null
                : lineageResolutionSupport.resolveLegalEntityResponse(asOfDb, asOfHistoryDb, currentIssuerId, validAt);
        List<LegalEntityLineageEntryResponse> issuerLineage = currentIssuerId == null
                ? List.of()
                : lineageResolutionSupport.resolveLegalEntityAncestors(asOfDb, asOfHistoryDb, currentIssuerId, validAt);

        NameHistoryResponse nameHistory = lineageResolutionSupport.buildSecurityNameHistory(asOfHistoryDb, securityId, securityLineage,
                currentIssuerId, issuerLineage, validAt);

        return new SecurityLineageResponse(security, securityLineage, currentIssuer,
                issuerLineage, nameHistory, validAt);
    }
}
