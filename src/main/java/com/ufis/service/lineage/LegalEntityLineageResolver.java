package com.ufis.service.lineage;

import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.dto.response.LegalEntityLineageEntryResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.repository.DatomicEnumMapper;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.service.DtoMapper;
import com.ufis.util.DateUtils;
import datomic.Database;
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
public final class LegalEntityLineageResolver {
    private final LineageQuerySupport querySupport;
    private final LegalEntityRepository legalEntityRepository;
    private final LineageRepository lineageRepository;
    private final DtoMapper dtoMapper;

    LegalEntityResponse resolveLegalEntityResponse(UUID entityId, Instant validAt) {
        Database db = querySupport.currentDb();
        return resolveLegalEntityResponse(db, db.history(), entityId, validAt);
    }

    LegalEntityResponse resolveLegalEntityResponse(Database db, Database historyDb, UUID entityId, Instant validAt) {
        return dtoMapper.toLegalEntityResponse(resolveLegalEntityMap(db, historyDb, entityId, validAt));
    }

    Map<String, Object> resolveLegalEntityMap(UUID entityId, Instant validAt) {
        Database db = querySupport.currentDb();
        return resolveLegalEntityMap(db, db.history(), entityId, validAt);
    }

    Map<String, Object> resolveLegalEntityMap(Database db, Database historyDb, UUID entityId, Instant validAt) {
        Map<String, Object> current = legalEntityRepository.findById(db, entityId);

        if (current == null) {
            log.warn("Legal entity not found during lineage resolution id={}", entityId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal entity not found");
        }

        if (validAt == null) {
            return current;
        }

        Map<String, Object> resolved = new HashMap<>(current);
        resolved.put(
                "name",
                querySupport.resolveAttributeAt(
                        querySupport.getAttributeHistory(db, historyDb, ":legal-entity/id", entityId, ":legal-entity/name"), validAt,
                        value -> (String) value,
                        (String) current.get("name")
                )
        );
        resolved.put(
                "state",
                querySupport.resolveAttributeAt(
                        querySupport.getAttributeHistory(db, historyDb, ":legal-entity/id", entityId, ":legal-entity/state"), validAt,
                        value -> DatomicEnumMapper.toLegalEntityState(querySupport.toRefValue(db, value)),
                        (LegalEntityState) current.get("state")
                )
        );
        resolved.put(
                "active",
                querySupport.resolveAttributeAt(
                        querySupport.getAttributeHistory(db, historyDb, ":legal-entity/id", entityId, ":legal-entity/active"), validAt,
                        value -> (Boolean) value,
                        (Boolean) current.get("active")
                )
        );
        return resolved;
    }

    List<LegalEntityLineageEntryResponse> resolveLegalEntityAncestors(UUID entityId, Instant validAt) {
        Database db = querySupport.currentDb();

        return resolveLegalEntityAncestors(db, db.history(), entityId, validAt);
    }

    List<LegalEntityLineageEntryResponse> resolveLegalEntityAncestors(Database db, Database historyDb, UUID entityId, Instant validAt) {
        List<EntityAncestorEdge> collected = new ArrayList<>();
        ArrayDeque<UUID> stack = new ArrayDeque<>();

        stack.push(entityId);

        Set<String> seenEdges = new HashSet<>();

        while (!stack.isEmpty()) {
            UUID currentId = stack.pop();
            for (Map<String, Object> lineage : lineageRepository.findEntityLineageByChild(db, currentId)) {
                UUID parentId = (UUID) lineage.get("parentId"), actionId = (UUID) lineage.get("actionId");
                ActionInfo actionInfo = querySupport.getActionInfo(db, actionId);

                if (validAt != null && actionInfo.validDate() != null && actionInfo.validDate().isAfter(validAt)) {
                    continue;
                }

                String edgeKey = parentId + "|" + currentId + "|" + actionId;

                if (!seenEdges.add(edgeKey)) {
                    continue;
                }

                collected.add(new EntityAncestorEdge(parentId, actionInfo));
                stack.push(parentId);
            }
        }

        collected.sort(Comparator.comparing(EntityAncestorEdge::actionInfo, LineageQuerySupport.ACTION_VALID_DATE_ORDER)
                .thenComparing(EntityAncestorEdge::parentId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(edge -> edge.actionInfo().actionId(), Comparator.nullsFirst(Comparator.naturalOrder())));

        List<LegalEntityLineageEntryResponse> entries = new ArrayList<>();
        for (EntityAncestorEdge edge : collected) {
            Map<String, Object> parent = resolveLegalEntityMap(db, historyDb, edge.parentId(), validAt);
            entries.add(new LegalEntityLineageEntryResponse(
                    edge.parentId(),
                    (String) parent.get("name"),
                    (LegalEntityType) parent.get("type"),
                    (LegalEntityState) parent.get("state"),
                    Boolean.TRUE.equals(parent.get("active")),
                    DateUtils.toInstant((Date) parent.get("foundedDate")),
                    edge.actionInfo().actionId(),
                    edge.actionInfo().actionType(),
                    edge.actionInfo().validDate()
            ));
        }
        return entries;
    }

    private record EntityAncestorEdge(UUID parentId, ActionInfo actionInfo) {}
}
