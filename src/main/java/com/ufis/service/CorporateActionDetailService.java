package com.ufis.service;

import com.ufis.dto.response.CorporateActionDetailResponse;
import com.ufis.dto.response.CorporateActionRecordResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.LineageRecordResponse;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import datomic.Connection;
import datomic.Database;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorporateActionDetailService {
    private final CorporateActionRepository corporateActionRepository;
    private final LineageRepository lineageRepository;
    private final SecurityRepository securityRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final DtoMapper dtoMapper;
    private final Connection connection;

    public CorporateActionDetailResponse getDetail(UUID actionId) {
        Database db = connection.db();

        Map<String, Object> actionMap = corporateActionRepository.findById(db, actionId);

        if (actionMap == null) {
            log.warn("Corporate action not found id={}", actionId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Corporate action not found");
        }

        CorporateActionRecordResponse action = dtoMapper.toCorporateActionRecordResponse(actionMap);

        List<Map<String, Object>> secLineage = lineageRepository.findSecurityLineageByAction(db, actionId);
        List<Map<String, Object>> entLineage = lineageRepository.findEntityLineageByAction(db, actionId);

        Set<UUID> sourceSecurityIds = new LinkedHashSet<>();
        Set<UUID> resultSecurityIds = new LinkedHashSet<>();
        Set<UUID> terminatedSecurityIds = new LinkedHashSet<>();

        for (Map<String, Object> record : secLineage) {
            UUID parentId = (UUID) record.get("parentSecurityId");
            UUID childId = (UUID) record.get("childSecurityId");

            sourceSecurityIds.add(parentId);
            if (childId != null) {
                resultSecurityIds.add(childId);
            } else {
                terminatedSecurityIds.add(parentId);
            }
        }

        Set<UUID> sourceEntityIds = new LinkedHashSet<>();
        Set<UUID> resultEntityIds = new LinkedHashSet<>();

        for (Map<String, Object> record : entLineage) {
            UUID parentId = (UUID) record.get("parentEntityId");
            UUID childId = (UUID) record.get("childEntityId");

            sourceEntityIds.add(parentId);
            if (childId != null) {
                resultEntityIds.add(childId);
            }
        }

        List<SecurityResponse> sourceSecurities = resolveSecurities(db, sourceSecurityIds);
        List<SecurityResponse> resultSecurities = resolveSecurities(db, resultSecurityIds);
        List<SecurityResponse> terminatedSecurities = resolveSecurities(db, terminatedSecurityIds);
        List<LegalEntityResponse> sourceEntities = resolveEntities(db, sourceEntityIds);
        List<LegalEntityResponse> resultEntities = resolveEntities(db, resultEntityIds);

        List<LineageRecordResponse> lineageRecords = new ArrayList<>();

        for (Map<String, Object> record : secLineage) {
            lineageRecords.add(dtoMapper.toSecurityLineageRecordResponse(
                    (UUID) record.get("lineageId"),
                    actionId,
                    (UUID) record.get("parentSecurityId"),
                    (UUID) record.get("childSecurityId")
            ));
        }

        for (Map<String, Object> record : entLineage) {
            lineageRecords.add(dtoMapper.toLegalEntityLineageRecordResponse(
                    (UUID) record.get("lineageId"),
                    actionId,
                    (UUID) record.get("parentEntityId"),
                    (UUID) record.get("childEntityId")
            ));
        }

        return new CorporateActionDetailResponse(action, sourceSecurities, resultSecurities,
                terminatedSecurities, sourceEntities, resultEntities, lineageRecords);
    }

    private List<SecurityResponse> resolveSecurities(Database db, Set<UUID> ids) {
        List<SecurityResponse> list = new ArrayList<>();

        for (UUID id : ids) {
            Map<String, Object> sec = securityRepository.findById(db, id);

            if (sec != null) {
                list.add(dtoMapper.toSecurityResponse(sec));
            }
        }

        return list;
    }

    private List<LegalEntityResponse> resolveEntities(Database db, Set<UUID> ids) {
        List<LegalEntityResponse> list = new ArrayList<>();

        for (UUID id : ids) {
            Map<String, Object> ent = legalEntityRepository.findById(db, id);

            if (ent != null) {
                list.add(dtoMapper.toLegalEntityResponse(ent));
            }
        }

        return list;
    }
}
