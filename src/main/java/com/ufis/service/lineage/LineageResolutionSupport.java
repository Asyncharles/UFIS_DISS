package com.ufis.service.lineage;

import com.ufis.dto.response.LegalEntityLineageEntryResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.NameHistoryResponse;
import com.ufis.dto.response.SecurityLineageEntryResponse;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DtoMapper;
import datomic.Connection;
import datomic.Database;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LineageResolutionSupport {
    private final SecurityLineageResolver securityLineageResolver;
    private final LegalEntityLineageResolver legalEntityLineageResolver;
    private final NameHistoryBuilder nameHistoryBuilder;

    public LineageResolutionSupport(Connection connection, SecurityRepository securityRepository, LegalEntityRepository legalEntityRepository,
                                    LineageRepository lineageRepository, CorporateActionRepository corporateActionRepository, DtoMapper dtoMapper) {
        LineageQuerySupport querySupport = new LineageQuerySupport(connection, corporateActionRepository);

        this.legalEntityLineageResolver = new LegalEntityLineageResolver(querySupport, legalEntityRepository, lineageRepository, dtoMapper);
        this.securityLineageResolver = new SecurityLineageResolver(querySupport, securityRepository, lineageRepository, dtoMapper, legalEntityLineageResolver);
        this.nameHistoryBuilder = new NameHistoryBuilder(querySupport);
    }

    public SecurityResponse resolveSecurityResponse(UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityResponse(securityId, validAt);
    }

    public SecurityResponse resolveSecurityResponse(Database db, Database historyDb, UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityResponse(db, historyDb, securityId, validAt);
    }

    public LegalEntityResponse resolveLegalEntityResponse(UUID entityId, Instant validAt) {
        return legalEntityLineageResolver.resolveLegalEntityResponse(entityId, validAt);
    }

    public LegalEntityResponse resolveLegalEntityResponse(Database db, Database historyDb, UUID entityId, Instant validAt) {
        return legalEntityLineageResolver.resolveLegalEntityResponse(db, historyDb, entityId, validAt);
    }

    public Map<String, Object> resolveSecurityMap(UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityMap(securityId, validAt);
    }

    public Map<String, Object> resolveSecurityMap(Database db, Database historyDb, UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityMap(db, historyDb, securityId, validAt);
    }

    public Map<String, Object> resolveLegalEntityMap(UUID entityId, Instant validAt) {
        return legalEntityLineageResolver.resolveLegalEntityMap(entityId, validAt);
    }

    public Map<String, Object> resolveLegalEntityMap(Database db, Database historyDb, UUID entityId, Instant validAt) {
        return legalEntityLineageResolver.resolveLegalEntityMap(db, historyDb, entityId, validAt);
    }

    public List<SecurityLineageEntryResponse> resolveSecurityAncestors(UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityAncestors(securityId, validAt);
    }

    public List<SecurityLineageEntryResponse> resolveSecurityAncestors(Database db, Database historyDb, UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityAncestors(db, historyDb, securityId, validAt);
    }

    public List<LegalEntityLineageEntryResponse> resolveLegalEntityAncestors(UUID entityId, Instant validAt) {
        return legalEntityLineageResolver.resolveLegalEntityAncestors(entityId, validAt);
    }

    public List<LegalEntityLineageEntryResponse> resolveLegalEntityAncestors(Database db, Database historyDb, UUID entityId, Instant validAt) {
        return legalEntityLineageResolver.resolveLegalEntityAncestors(db, historyDb, entityId, validAt);
    }

    public NameHistoryResponse buildSecurityNameHistory(UUID securityId, List<SecurityLineageEntryResponse> securityLineage, UUID currentIssuerId,
                                                        List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        return nameHistoryBuilder.buildSecurityNameHistory(securityId, securityLineage, currentIssuerId, issuerLineage, validAt);
    }

    public NameHistoryResponse buildSecurityNameHistory(Database historyDb, UUID securityId, List<SecurityLineageEntryResponse> securityLineage,
                                                        UUID currentIssuerId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        return nameHistoryBuilder.buildSecurityNameHistory(historyDb, securityId, securityLineage, currentIssuerId, issuerLineage, validAt);
    }

    public NameHistoryResponse buildLegalEntityNameHistory(UUID entityId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        return nameHistoryBuilder.buildLegalEntityNameHistory(entityId, issuerLineage, validAt);
    }

    public NameHistoryResponse buildLegalEntityNameHistory(Database historyDb, UUID entityId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        return nameHistoryBuilder.buildLegalEntityNameHistory(historyDb, entityId, issuerLineage, validAt);
    }

    public UUID resolveSecurityIssuerId(UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityIssuerId(securityId, validAt);
    }

    public UUID resolveSecurityIssuerId(Database db, Database historyDb, UUID securityId, Instant validAt) {
        return securityLineageResolver.resolveSecurityIssuerId(db, historyDb, securityId, validAt);
    }
}
