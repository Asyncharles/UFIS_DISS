package com.ufis.service.lineage;

import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import com.ufis.dto.response.IssuerRefResponse;
import com.ufis.dto.response.SecurityLineageEntryResponse;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.repository.DatomicEnumMapper;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DtoMapper;
import com.ufis.util.DateUtils;
import datomic.Database;
import datomic.Entity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
final class SecurityLineageResolver {
    private final LineageQuerySupport querySupport;
    private final SecurityRepository securityRepository;
    private final LineageRepository lineageRepository;
    private final DtoMapper dtoMapper;
    private final LegalEntityLineageResolver legalEntityLineageResolver;

    SecurityResponse resolveSecurityResponse(UUID securityId, Instant validAt) {
        Database db = querySupport.currentDb();

        return resolveSecurityResponse(db, db.history(), securityId, validAt);
    }

    SecurityResponse resolveSecurityResponse(Database db, Database historyDb, UUID securityId, Instant validAt) {
        return dtoMapper.toSecurityResponse(resolveSecurityMap(db, historyDb, securityId, validAt));
    }

    Map<String, Object> resolveSecurityMap(UUID securityId, Instant validAt) {
        Database db = querySupport.currentDb();

        return resolveSecurityMap(db, db.history(), securityId, validAt);
    }

    Map<String, Object> resolveSecurityMap(Database db, Database historyDb, UUID securityId, Instant validAt) {
        Map<String, Object> current = securityRepository.findById(db, securityId);

        if (current == null) {
            log.warn("Security not found during lineage resolution id={}", securityId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Security not found");
        }

        if (validAt == null) {
            return current;
        }

        Map<String, Object> resolved = new HashMap<>(current);
        resolved.put("name",
                querySupport.resolveAttributeAt(
                        querySupport.getAttributeHistory(db, historyDb, ":security/id", securityId, ":security/name"), validAt,
                        value -> (String) value,
                        (String) current.get("name")
                )
        );
        resolved.put("isin",
                querySupport.resolveAttributeAt(
                        querySupport.getAttributeHistory(db, historyDb, ":security/id", securityId, ":security/isin"), validAt,
                        value -> (String) value,
                        (String) current.get("isin")
                )
        );
        resolved.put("state",
                querySupport.resolveAttributeAt(
                        querySupport.getAttributeHistory(db, historyDb, ":security/id", securityId, ":security/state"), validAt,
                        value -> DatomicEnumMapper.toSecurityState(querySupport.toRefValue(db, value)),
                        (SecurityState) current.get("state")
                )
        );
        resolved.put("active",
                querySupport.resolveAttributeAt(
                        querySupport.getAttributeHistory(db, historyDb, ":security/id", securityId, ":security/active"), validAt,
                        value -> (Boolean) value,
                        (Boolean) current.get("active")
                )
        );

        UUID issuerId = resolveSecurityIssuerId(db, historyDb, securityId, validAt);

        if (issuerId == null) {
            resolved.put("issuer", null);
            return resolved;
        }

        Map<String, Object> issuer = legalEntityLineageResolver.resolveLegalEntityMap(db, historyDb, issuerId, validAt);
        Map<String, Object> issuerRef = new HashMap<>();
        issuerRef.put("id", issuerId);
        issuerRef.put("name", issuer.get("name"));
        resolved.put("issuer", issuerRef);

        return resolved;
    }

    List<SecurityLineageEntryResponse> resolveSecurityAncestors(UUID securityId, Instant validAt) {
        Database db = querySupport.currentDb();

        return resolveSecurityAncestors(db, db.history(), securityId, validAt);
    }

    List<SecurityLineageEntryResponse> resolveSecurityAncestors(Database db, Database historyDb, UUID securityId, Instant validAt) {
        List<SecurityAncestorEdge> collected = new ArrayList<>();
        ArrayDeque<UUID> stack = new ArrayDeque<>();

        stack.push(securityId);

        Set<String> seenEdges = new HashSet<>();

        while (!stack.isEmpty()) {
            UUID currentId = stack.pop();
            for (Map<String, Object> lineage : lineageRepository.findSecurityLineageByChild(db, currentId)) {
                UUID parentId = (UUID) lineage.get("parentId");
                UUID actionId = (UUID) lineage.get("actionId");
                ActionInfo actionInfo = querySupport.getActionInfo(db, actionId);
                if (validAt != null && actionInfo.validDate() != null && actionInfo.validDate().isAfter(validAt)) {
                    continue;
                }

                String edgeKey = parentId + "|" + currentId + "|" + actionId;
                if (!seenEdges.add(edgeKey)) {
                    continue;
                }

                collected.add(new SecurityAncestorEdge(parentId, actionInfo));
                stack.push(parentId);
            }
        }

        collected.sort(Comparator.comparing(SecurityAncestorEdge::actionInfo, LineageQuerySupport.ACTION_VALID_DATE_ORDER)
                .thenComparing(SecurityAncestorEdge::parentId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(edge -> edge.actionInfo().actionId(), Comparator.nullsFirst(Comparator.naturalOrder())));

        List<SecurityLineageEntryResponse> entries = new ArrayList<>();

        for (SecurityAncestorEdge edge : collected) {
            Map<String, Object> parent = resolveSecurityMap(db, historyDb, edge.parentId(), validAt);
            @SuppressWarnings("unchecked")
            Map<String, Object> issuer = (Map<String, Object>) parent.get("issuer");
            entries.add(new SecurityLineageEntryResponse(
                    edge.parentId(),
                    (String) parent.get("name"),
                    (String) parent.get("isin"),
                    (SecurityType) parent.get("type"),
                    (SecurityState) parent.get("state"),
                    Boolean.TRUE.equals(parent.get("active")),
                    DateUtils.toInstant((Date) parent.get("issueDate")),
                    DateUtils.toInstant((Date) parent.get("maturityDate")),
                    issuer == null ? null : new IssuerRefResponse((UUID) issuer.get("id"), (String) issuer.get("name")),
                    edge.actionInfo().actionId(),
                    edge.actionInfo().actionType(),
                    edge.actionInfo().validDate()
            ));
        }

        return entries;
    }

    UUID resolveSecurityIssuerId(UUID securityId, Instant validAt) {
        Database db = querySupport.currentDb();

        return resolveSecurityIssuerId(db, db.history(), securityId, validAt);
    }

    UUID resolveSecurityIssuerId(Database db, Database historyDb, UUID securityId, Instant validAt) {
        Map<String, Object> current = securityRepository.findById(db, securityId);

        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Security not found");
        }

        if (validAt == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> issuer = (Map<String, Object>) current.get("issuer");
            return issuer == null ? null : (UUID) issuer.get("id");
        }

        Entity issuerEntity = querySupport.resolveAttributeAt(
                querySupport.getAttributeHistory(db, historyDb, ":security/id", securityId, ":security/issuer"), validAt,
                value -> querySupport.toEntityRef(db, value),
                null
        );

        return issuerEntity == null ? null : (UUID) issuerEntity.get(":legal-entity/id");
    }

    private record SecurityAncestorEdge(UUID parentId, ActionInfo actionInfo) {}
}
