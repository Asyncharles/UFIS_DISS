package com.ufis.service;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityRole;
import com.ufis.domain.enums.SecurityRole;
import com.ufis.dto.response.CorporateActionRecordResponse;
import com.ufis.dto.response.LegalEntityActionListEntryResponse;
import com.ufis.dto.response.SecurityActionListEntryResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import datomic.Connection;
import datomic.Database;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActionService {
    private static final Comparator<ActionEnvelope<?>> ACTION_ORDER =
            Comparator.comparing((ActionEnvelope<?> envelope) -> envelope.action().validDate())
                    .thenComparing(envelope -> envelope.action().id());

    private final Connection connection;
    private final SecurityRepository securityRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final LineageRepository lineageRepository;
    private final DtoMapper dtoMapper;

    public List<SecurityActionListEntryResponse> getSecurityActions(UUID securityId) {
        if (securityRepository.findById(securityId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Security not found");
        }

        Database db = connection.db();
        Map<UUID, SecurityRole> rolesByActionId = new LinkedHashMap<>();

        for (Map<String, Object> lineage : lineageRepository.findSecurityLineageByParent(db, securityId)) {
            rolesByActionId.putIfAbsent((UUID) lineage.get("actionId"), SecurityRole.SOURCE);
        }

        for (Map<String, Object> lineage : lineageRepository.findSecurityLineageByChild(db, securityId)) {
            rolesByActionId.putIfAbsent((UUID) lineage.get("actionId"), SecurityRole.RESULT);
        }

        for (UUID actionId : findSecuritySubjectActionIds(securityId)) {
            rolesByActionId.putIfAbsent(actionId, SecurityRole.SUBJECT);
        }

        List<ActionEnvelope<SecurityRole>> actions = new ArrayList<>();

        for (Map.Entry<UUID, SecurityRole> entry : rolesByActionId.entrySet()) {
            actions.add(new ActionEnvelope<>(
                    dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(entry.getKey())),
                    entry.getValue()
            ));
        }

        actions.sort(ACTION_ORDER);

        return actions.stream()
                .map(action -> new SecurityActionListEntryResponse(action.action(), action.role()))
                .toList();
    }

    public List<LegalEntityActionListEntryResponse> getLegalEntityActions(UUID entityId) {
        if (legalEntityRepository.findById(entityId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal entity not found");
        }

        Database db = connection.db();
        Map<UUID, LegalEntityRole> rolesByActionId = new LinkedHashMap<>();

        for (Map<String, Object> lineage : lineageRepository.findEntityLineageByParent(db, entityId)) {
            UUID actionId = (UUID) lineage.get("actionId");
            CorporateActionType type = dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(actionId)).type();
            LegalEntityRole role = type == CorporateActionType.SPIN_OFF ? LegalEntityRole.SUBJECT : LegalEntityRole.SOURCE;
            rolesByActionId.putIfAbsent(actionId, role);
        }

        for (Map<String, Object> lineage : lineageRepository.findEntityLineageByChild(db, entityId)) {
            UUID actionId = (UUID) lineage.get("actionId");
            CorporateActionType type = dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(actionId)).type();
            LegalEntityRole role = type == CorporateActionType.ACQUISITION ? LegalEntityRole.ACQUIRER : LegalEntityRole.RESULT;
            rolesByActionId.putIfAbsent(actionId, role);
        }

        for (UUID actionId : findEntitySubjectActionIds(entityId)) {
            rolesByActionId.putIfAbsent(actionId, LegalEntityRole.SUBJECT);
        }

        List<ActionEnvelope<LegalEntityRole>> actions = new ArrayList<>();

        for (Map.Entry<UUID, LegalEntityRole> entry : rolesByActionId.entrySet()) {
            actions.add(new ActionEnvelope<>(dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(entry.getKey())), entry.getValue()));
        }

        actions.sort(ACTION_ORDER);

        return actions.stream()
                .map(action -> new LegalEntityActionListEntryResponse(action.action(), action.role()))
                .toList();
    }

    private List<UUID> findSecuritySubjectActionIds(UUID securityId) {
        Map<UUID, UUID> actionIds = new LinkedHashMap<>();

        for (String attr : List.of(":security/name", ":security/isin", ":security/issuer")) {
            for (UUID actionId : findActionIdsForAttribute(":security/id", securityId, attr)) {
                actionIds.putIfAbsent(actionId, actionId);
            }
        }

        return List.copyOf(actionIds.keySet());
    }

    private List<UUID> findEntitySubjectActionIds(UUID entityId) {
        return findActionIdsForAttribute(":legal-entity/id", entityId, ":legal-entity/name");
    }

    private List<UUID> findActionIdsForAttribute(String lookupAttr, UUID id, String attr) {
        Collection<List<Object>> rows = Peer.q(
                "[:find ?aid " +
                " :in $ ?lookup-attr ?id ?attr " +
                " :where [?e ?lookup-attr ?id] " +
                "        [?e ?attr _ ?tx true] " +
                "        [?ca :corporate-action/id ?aid ?tx true]]",
                connection.db().history(),
                Util.read(lookupAttr),
                id,
                Util.read(attr)
        );

        Map<UUID, UUID> actionIds = new LinkedHashMap<>();

        for (List<Object> row : rows) {
            UUID actionId = (UUID) row.get(0);
            actionIds.putIfAbsent(actionId, actionId);
        }

        return List.copyOf(actionIds.keySet());
    }

    private record ActionEnvelope<R>(CorporateActionRecordResponse action, R role) {}
}
