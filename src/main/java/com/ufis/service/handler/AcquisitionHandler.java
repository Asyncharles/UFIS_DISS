package com.ufis.service.handler;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.dto.request.AcquisitionRequest;
import com.ufis.dto.response.CorporateActionResponse;
import com.ufis.dto.response.LineageRecordResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DtoMapper;
import com.ufis.service.IssuerTransitionService;
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
public class AcquisitionHandler {
    private final Connection connection;
    private final CorporateActionRepository corporateActionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final SecurityRepository securityRepository;
    private final LineageRepository lineageRepository;
    private final CorporateActionValidator corporateActionValidator;
    private final DtoMapper dtoMapper;
    private final IssuerTransitionService issuerTransitionService;

    @SuppressWarnings("unchecked")
    public CorporateActionResponse handle(AcquisitionRequest request) {
        corporateActionValidator.validateLegalEntityActionType(CorporateActionType.ACQUISITION);
        corporateActionValidator.validateDistinctIds(request.acquirerEntityId(), request.targetEntityId(), "Acquirer and target must be different legal entities");
        corporateActionValidator.requireActiveLegalEntity(request.acquirerEntityId());
        corporateActionValidator.requireActiveLegalEntity(request.targetEntityId());
        corporateActionValidator.ensureNoCircularLegalEntityLineage(request.targetEntityId(), request.acquirerEntityId());

        List<Map<String, Object>> targetSecurities = securityRepository.findByIssuerId(connection.db(), request.targetEntityId());
        corporateActionValidator.ensureNoDuplicateSecurityValidDate(
                targetSecurities.stream().map(security -> (UUID) security.get("id")).toList(),
                request.validDate()
        );

        UUID actionId = UUID.randomUUID();
        UUID lineageId = UUID.randomUUID();
        Object actionTempId = Peer.tempid(":db.part/user");
        Object lineageTempId = Peer.tempid(":db.part/user");

        List<Object> tx = new ArrayList<>();
        tx.add(corporateActionRepository.buildCreateTxMap(
                actionId,
                CorporateActionType.ACQUISITION,
                Date.from(request.validDate()),
                request.description(),
                null,
                actionTempId
        ));
        tx.add(legalEntityRepository.buildStateTransitionTxMap(request.targetEntityId(), LegalEntityState.ACQUIRED));
        tx.add(lineageRepository.buildLegalEntityLineageTxMap(
                Util.list(":legal-entity/id", request.targetEntityId()),
                Util.list(":legal-entity/id", request.acquirerEntityId()),
                actionTempId,
                lineageTempId,
                lineageId
        ));

        IssuerTransitionService.IssuerTransitionPlan issuerTransitionPlan =
                issuerTransitionService.buildIssuerTransitionPlan(
                        targetSecurities,
                        Util.list(":legal-entity/id", request.acquirerEntityId()),
                        request.acquirerEntityId()
                );
        tx.addAll(issuerTransitionPlan.txOperations());

        try {
            connection.transact(tx).get();
            log.info("Processed ACQUISITION acquirer={}, target={}, actionId={}", request.acquirerEntityId(), request.targetEntityId(), actionId);
            LegalEntityResponse terminatedEntity = dtoMapper.toLegalEntityResponse(
                    legalEntityRepository.findById(request.targetEntityId())
            );
            LineageRecordResponse lineageRecord = dtoMapper.toLegalEntityLineageRecordResponse(
                    lineageId,
                    actionId,
                    request.targetEntityId(),
                    request.acquirerEntityId()
            );
            return CorporateActionResponse.builder(
                    dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(actionId))
            )
                    .terminatedEntities(List.of(terminatedEntity))
                    .lineageRecords(List.of(lineageRecord))
                    .issuerUpdates(issuerTransitionPlan.issuerUpdates())
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process ACQUISITION acquirer={}, target={}", request.acquirerEntityId(), request.targetEntityId(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to acquire legal entity", ex);
        }
    }
}
