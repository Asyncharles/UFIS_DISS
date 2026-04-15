package com.ufis.service.handler;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.dto.request.SpinOffRequest;
import com.ufis.dto.request.SpinOffSecurityMappingRequest;
import com.ufis.dto.response.CorporateActionResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.LineageRecordResponse;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DtoMapper;
import com.ufis.validation.CorporateActionValidator;
import datomic.Connection;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpinOffHandler {
    private final Connection connection;
    private final CorporateActionRepository corporateActionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final SecurityRepository securityRepository;
    private final LineageRepository lineageRepository;
    private final CorporateActionValidator corporateActionValidator;
    private final DtoMapper dtoMapper;

    @SuppressWarnings("unchecked")
    public CorporateActionResponse handle(SpinOffRequest request) {
        corporateActionValidator.validateLegalEntityActionType(CorporateActionType.SPIN_OFF);
        corporateActionValidator.requireActiveLegalEntity(request.parentEntityId());

        List<SpinOffSecurityMappingRequest> securityMappings = request.securityMappings() == null ? List.of() : request.securityMappings();

        for (SpinOffSecurityMappingRequest mapping : securityMappings) {
            Map<String, Object> parentSecurity = corporateActionValidator.requireActiveSecurity(mapping.parentSecurityId());

            corporateActionValidator.validateSecurityFields(mapping.newSecurity().type(), mapping.newSecurity().maturityDate());
            corporateActionValidator.ensureNoDuplicateSecurityValidDate(mapping.parentSecurityId(), request.validDate());

            Map<String, Object> issuer = (Map<String, Object>) parentSecurity.get("issuer");
            if (issuer == null || !request.parentEntityId().equals(issuer.get("id"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent security must be issued by parent entity");
            }
        }

        UUID actionId = UUID.randomUUID();
        UUID newEntityId = UUID.randomUUID();
        UUID entityLineageId = UUID.randomUUID();
        Object actionTempId = Peer.tempid(":db.part/user");
        Object entityTempId = Peer.tempid(":db.part/user");
        Object entityLineageTempId = Peer.tempid(":db.part/user");

        List<Object> tx = new ArrayList<>();
        tx.add(corporateActionRepository.buildCreateTxMap(
                actionId,
                CorporateActionType.SPIN_OFF,
                Date.from(request.validDate()),
                request.description(),
                null,
                actionTempId
        ));
        tx.add(legalEntityRepository.buildCreateTxMap(
                newEntityId,
                request.newEntity().name(),
                request.newEntity().type(),
                Date.from(request.validDate()),
                entityTempId
        ));
        tx.add(lineageRepository.buildLegalEntityLineageTxMap(
                Util.list(":legal-entity/id", request.parentEntityId()),
                entityTempId,
                actionTempId,
                entityLineageTempId,
                entityLineageId
        ));

        List<UUID> createdSecurityIds = new ArrayList<>();
        List<LineageRecordResponse> lineageRecords = new ArrayList<>();
        lineageRecords.add(dtoMapper.toLegalEntityLineageRecordResponse(
                entityLineageId,
                actionId,
                request.parentEntityId(),
                newEntityId
        ));

        for (SpinOffSecurityMappingRequest mapping : securityMappings) {
            UUID createdSecurityId = UUID.randomUUID();
            UUID securityLineageId = UUID.randomUUID();
            Object securityTempId = Peer.tempid(":db.part/user");
            Object securityLineageTempId = Peer.tempid(":db.part/user");

            tx.add(securityRepository.buildCreateTxMap(
                    createdSecurityId,
                    mapping.newSecurity().name(),
                    mapping.newSecurity().type(),
                    entityTempId,
                    Date.from(request.validDate()),
                    mapping.newSecurity().isin(),
                    mapping.newSecurity().maturityDate() == null ? null : Date.from(mapping.newSecurity().maturityDate()),
                    securityTempId
            ));
            tx.add(lineageRepository.buildSecurityLineageTxMap(
                    Util.list(":security/id", mapping.parentSecurityId()),
                    securityTempId,
                    actionTempId,
                    securityLineageTempId,
                    securityLineageId
            ));

            createdSecurityIds.add(createdSecurityId);
            lineageRecords.add(dtoMapper.toSecurityLineageRecordResponse(
                    securityLineageId,
                    actionId,
                    mapping.parentSecurityId(),
                    createdSecurityId
            ));
        }

        try {
            connection.transact(tx).get();
            log.info("Processed SPIN_OFF parent={}, newEntity={}, actionId={}", request.parentEntityId(), newEntityId, actionId);

            LegalEntityResponse createdEntity = dtoMapper.toLegalEntityResponse(
                    legalEntityRepository.findById(newEntityId)
            );
            List<SecurityResponse> createdSecurities = createdSecurityIds.stream()
                    .map(securityRepository::findById)
                    .map(dtoMapper::toSecurityResponse)
                    .toList();

            return CorporateActionResponse.builder(dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(actionId)))
                    .createdEntities(List.of(createdEntity))
                    .createdSecurities(createdSecurities)
                    .lineageRecords(lineageRecords)
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process SPIN_OFF for parent={}", request.parentEntityId(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process spin-off", ex);
        }
    }
}
