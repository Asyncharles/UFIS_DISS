package com.ufis.validation;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import datomic.Connection;
import datomic.Database;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class CorporateActionValidator {
    private static final Set<CorporateActionType> SECURITY_ACTION_TYPES = Set.of(
            CorporateActionType.MERGER,
            CorporateActionType.SPIN_OFF,
            CorporateActionType.NAME_CHANGE,
            CorporateActionType.STOCK_SPLIT,
            CorporateActionType.REDEMPTION
    );

    private static final Set<CorporateActionType> LEGAL_ENTITY_ACTION_TYPES = Set.of(
            CorporateActionType.MERGER,
            CorporateActionType.ACQUISITION,
            CorporateActionType.SPIN_OFF,
            CorporateActionType.NAME_CHANGE
    );

    private final Connection connection;
    private final LegalEntityRepository legalEntityRepository;
    private final SecurityRepository securityRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final LineageRepository lineageRepository;

    public Map<String, Object> requireLegalEntity(UUID id) {
        Map<String, Object> entity = legalEntityRepository.findById(id);

        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal entity not found");
        }

        return entity;
    }

    public Map<String, Object> requireSecurity(UUID id) {
        Map<String, Object> security = securityRepository.findById(id);

        if (security == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Security not found");
        }

        return security;
    }

    public Map<String, Object> requireActiveLegalEntity(UUID id) {
        Map<String, Object> entity = requireLegalEntity(id);

        if (!Boolean.TRUE.equals(entity.get("active"))) {
            log.warn("Legal entity is inactive id={}", id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Legal entity is inactive");
        }

        validateLegalEntityStateConsistency((LegalEntityState) entity.get("state"), (Boolean) entity.get("active"));
        return entity;
    }

    public Map<String, Object> requireActiveSecurity(UUID id) {
        Map<String, Object> security = requireSecurity(id);

        if (!Boolean.TRUE.equals(security.get("active"))) {
            log.warn("Security is inactive id={}", id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Security is inactive");
        }

        validateSecurityStateConsistency((SecurityState) security.get("state"), (Boolean) security.get("active"));
        return security;
    }

    public void validateSecurityStateConsistency(SecurityState state, boolean active) {
        boolean expected = state == SecurityState.ACTIVE;
        if (active != expected) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Security state and active flag are inconsistent");
        }
    }

    public void validateLegalEntityStateConsistency(LegalEntityState state, boolean active) {
        boolean expected = state == LegalEntityState.ACTIVE;
        if (active != expected) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Legal entity state and active flag are inconsistent");
        }
    }

    public void validateSecurityActionType(CorporateActionType type) {
        if (!SECURITY_ACTION_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corporate action type does not apply to securities");
        }
    }

    public void validateSecurityFields(SecurityType type, Instant maturityDate) {
        if (type == SecurityType.BOND && maturityDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maturityDate is required for BOND");
        }
        if (type == SecurityType.EQUITY && maturityDate != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maturityDate must be null for EQUITY");
        }
    }

    public void validateLegalEntityActionType(CorporateActionType type) {
        if (!LEGAL_ENTITY_ACTION_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corporate action type does not apply to legal entities");
        }
    }

    public void validateDistinctIds(UUID firstId, UUID secondId, String message) {
        if (firstId.equals(secondId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    public void validateDistinctIds(Collection<UUID> ids, String message) {
        if (ids.size() != new HashSet<>(ids).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    public void ensureLegalEntityResultIsNew(UUID id) {
        if (legalEntityRepository.findById(id) != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Result legal entity must be new");
        }
    }

    public void ensureSecurityResultIsNew(UUID id) {
        if (securityRepository.findById(id) != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Result security must be new");
        }
    }

    public void ensureNoDuplicateSecurityValidDate(UUID securityId, Instant validDate) {
        Database db = connection.db();
        ensureNoDuplicateSecurityValidDate(db, securityId, validDate);
    }

    private void ensureNoDuplicateSecurityValidDate(Database db, UUID securityId, Instant validDate) {
        Date targetDate = Date.from(validDate);
        Set<UUID> actionIds = findSecurityActionIds(db, securityId);

        for (UUID actionId : actionIds) {
            Map<String, Object> action = corporateActionRepository.findById(db, actionId);

            if (action != null && targetDate.equals(action.get("validDate"))) {
                log.warn("Duplicate validDate for security={}, validDate={}", securityId, validDate);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Security already has a corporate action on this validDate");
            }
        }
    }

    public void ensureNoDuplicateSecurityValidDate(Collection<UUID> securityIds, Instant validDate) {
        Database db = connection.db();

        for (UUID securityId : securityIds) {
            ensureNoDuplicateSecurityValidDate(db, securityId, validDate);
        }
    }

    public void ensureNoCircularSecurityLineage(UUID parentSecurityId, UUID childSecurityId) {
        if (childSecurityId == null) {
            return;
        }

        Database db = connection.db();
        if (parentSecurityId.equals(childSecurityId) || isSecurityAncestor(db, childSecurityId, parentSecurityId, new HashSet<>())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Security lineage would become circular");
        }
    }

    public void ensureNoCircularLegalEntityLineage(UUID parentEntityId, UUID childEntityId) {
        if (childEntityId == null) {
            return;
        }

        Database db = connection.db();
        if (parentEntityId.equals(childEntityId) || isLegalEntityAncestor(db, childEntityId, parentEntityId, new HashSet<>())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Legal entity lineage would become circular");
        }
    }

    private boolean isSecurityAncestor(Database db, UUID ancestorCandidate, UUID currentSecurityId, Set<UUID> visited) {
        if (!visited.add(currentSecurityId)) {
            return false;
        }

        for (Map<String, Object> lineage : lineageRepository.findSecurityLineageByChild(db, currentSecurityId)) {
            UUID parentId = (UUID) lineage.get("parentId");
            if (ancestorCandidate.equals(parentId) || isSecurityAncestor(db, ancestorCandidate, parentId, visited)) {
                return true;
            }
        }

        return false;
    }

    private Set<UUID> findSecurityActionIds(Database db, UUID securityId) {
        Set<UUID> actionIds = new HashSet<>();
        for (Map<String, Object> lineage : lineageRepository.findSecurityLineageByChild(db, securityId)) {
            actionIds.add((UUID) lineage.get("actionId"));
        }
        for (Map<String, Object> lineage : lineageRepository.findSecurityLineageByParent(db, securityId)) {
            actionIds.add((UUID) lineage.get("actionId"));
        }

        Database historyDb = db.history();
        collectSecurityActionIdsFromHistory(historyDb, securityId, ":security/name", actionIds);
        collectSecurityActionIdsFromHistory(historyDb, securityId, ":security/isin", actionIds);
        collectSecurityActionIdsFromHistory(historyDb, securityId, ":security/issuer", actionIds);
        collectSecurityActionIdsFromHistory(historyDb, securityId, ":security/state", actionIds);
        return actionIds;
    }

    private void collectSecurityActionIdsFromHistory(Database historyDb, UUID securityId, String attribute, Set<UUID> actionIds) {
        Collection<List<Object>> results = Peer.q(
                "[:find ?aid " +
                " :in $ ?security-id ?attr " +
                " :where [?se :security/id ?security-id] " +
                "        [?se ?attr _ ?tx true] " +
                "        [?ca :corporate-action/id ?aid ?tx true]]",
                historyDb,
                securityId,
                Util.read(attribute)
        );

        for (List<Object> row : results) {
            actionIds.add((UUID) row.get(0));
        }
    }

    private boolean isLegalEntityAncestor(Database db, UUID ancestorCandidate, UUID currentEntityId, Set<UUID> visited) {
        if (!visited.add(currentEntityId)) {
            return false;
        }

        for (Map<String, Object> lineage : lineageRepository.findEntityLineageByChild(db, currentEntityId)) {
            UUID parentId = (UUID) lineage.get("parentId");
            if (ancestorCandidate.equals(parentId) || isLegalEntityAncestor(db, ancestorCandidate, parentId, visited)) {
                return true;
            }
        }

        return false;
    }
}
