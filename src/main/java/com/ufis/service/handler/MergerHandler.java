package com.ufis.service.handler;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.SecurityState;
import com.ufis.dto.request.MergerRequest;
import com.ufis.dto.request.MergerSecurityMappingRequest;
import com.ufis.dto.response.CorporateActionResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.LineageRecordResponse;
import com.ufis.dto.response.SecurityResponse;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MergerHandler {
    private final Connection connection;
    private final CorporateActionRepository corporateActionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final SecurityRepository securityRepository;
    private final LineageRepository lineageRepository;
    private final CorporateActionValidator corporateActionValidator;
    private final DtoMapper dtoMapper;
    private final IssuerTransitionService issuerTransitionService;

    @SuppressWarnings("unchecked")
    public CorporateActionResponse handle(MergerRequest request) {
        MergerValidation validation = validateMergerRequest(request);
        MergerTransactionPlan transactionPlan = buildMergerTransaction(request, validation);

        try {
            connection.transact(transactionPlan.tx()).get();
            log.info("Processed MERGER sources={}, resultEntity={}, actionId={}",
                    request.sourceEntityIds(), transactionPlan.resultEntityId(), transactionPlan.actionId());
            return buildMergerResponse(transactionPlan);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process MERGER sources={}", request.sourceEntityIds(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process merger", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private MergerValidation validateMergerRequest(MergerRequest request) {
        corporateActionValidator.validateLegalEntityActionType(CorporateActionType.MERGER);
        corporateActionValidator.validateDistinctIds(request.sourceEntityIds(), "Source legal entities must be distinct");

        Set<UUID> sourceEntityIds = new LinkedHashSet<>(request.sourceEntityIds());
        for (UUID sourceEntityId : sourceEntityIds) {
            corporateActionValidator.requireActiveLegalEntity(sourceEntityId);
        }

        List<MergerSecurityMappingRequest> securityMappings = request.securityMappings() == null ? List.of() : request.securityMappings();
        Map<UUID, Map<String, Object>> mappedSourceSecurities = validateMergerSecurityMappings(sourceEntityIds, securityMappings);
        Map<UUID, Map<String, Object>> allSourceSecurities = collectActiveSourceSecurities(sourceEntityIds);

        corporateActionValidator.ensureNoDuplicateSecurityValidDate(allSourceSecurities.keySet(), request.validDate());
        return new MergerValidation(sourceEntityIds, securityMappings, mappedSourceSecurities, allSourceSecurities);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Map<String, Object>> validateMergerSecurityMappings(Set<UUID> sourceEntityIds, List<MergerSecurityMappingRequest> securityMappings) {
        Map<UUID, Map<String, Object>> mappedSourceSecurities = new LinkedHashMap<>();

        for (MergerSecurityMappingRequest mapping : securityMappings) {
            corporateActionValidator.validateDistinctIds(
                    mapping.sourceSecurityIds(),
                    "Source securities within a merger mapping must be distinct"
            );
            corporateActionValidator.validateSecurityFields(
                    mapping.resultSecurity().type(),
                    mapping.resultSecurity().maturityDate()
            );

            for (UUID securityId : mapping.sourceSecurityIds()) {
                if (mappedSourceSecurities.containsKey(securityId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source security cannot appear in multiple merger mappings");
                }

                Map<String, Object> sourceSecurity = corporateActionValidator.requireActiveSecurity(securityId);
                Map<String, Object> issuer = (Map<String, Object>) sourceSecurity.get("issuer");
                if (issuer == null || !sourceEntityIds.contains(issuer.get("id"))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mapped source security must be issued by a source entity");
                }
                mappedSourceSecurities.put(securityId, sourceSecurity);
            }
        }

        return mappedSourceSecurities;
    }

    private Map<UUID, Map<String, Object>> collectActiveSourceSecurities(Set<UUID> sourceEntityIds) {
        Map<UUID, Map<String, Object>> allSourceSecurities = new LinkedHashMap<>();
        for (UUID sourceEntityId : sourceEntityIds) {
            for (Map<String, Object> security : securityRepository.findByIssuerId(connection.db(), sourceEntityId)) {
                if (Boolean.TRUE.equals(security.get("active"))) {
                    allSourceSecurities.put((UUID) security.get("id"), security);
                }
            }
        }
        return allSourceSecurities;
    }

    private MergerTransactionPlan buildMergerTransaction(MergerRequest request, MergerValidation validation) {
        UUID actionId = UUID.randomUUID();
        UUID resultEntityId = UUID.randomUUID();
        Object actionTempId = Peer.tempid(":db.part/user");
        Object resultEntityTempId = Peer.tempid(":db.part/user");

        List<Object> tx = new ArrayList<>();
        tx.add(corporateActionRepository.buildCreateTxMap(
                actionId,
                CorporateActionType.MERGER,
                Date.from(request.validDate()),
                request.description(),
                null,
                actionTempId
        ));
        tx.add(legalEntityRepository.buildCreateTxMap(
                resultEntityId,
                request.resultEntity().name(),
                request.resultEntity().type(),
                Date.from(request.validDate()),
                resultEntityTempId
        ));

        List<UUID> terminatedEntityIds = new ArrayList<>();
        List<UUID> createdSecurityIds = new ArrayList<>();
        List<UUID> terminatedSecurityIds = new ArrayList<>();
        List<LineageRecordResponse> lineageRecords = new ArrayList<>();

        addEntityTransitions(validation.sourceEntityIds(), actionId, resultEntityId,
                actionTempId, resultEntityTempId, tx, terminatedEntityIds, lineageRecords);

        addSecurityTransitions(validation.securityMappings(), request.validDate(), actionId,
                resultEntityTempId, actionTempId, tx, createdSecurityIds, terminatedSecurityIds, lineageRecords);

        List<Map<String, Object>> survivingSourceSecurities = validation.allSourceSecurities().entrySet().stream()
                .filter(entry -> !validation.mappedSourceSecurities().containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        IssuerTransitionService.IssuerTransitionPlan issuerTransitionPlan =
                issuerTransitionService.buildIssuerTransitionPlan(
                        survivingSourceSecurities,
                        resultEntityTempId,
                        resultEntityId
                );
        tx.addAll(issuerTransitionPlan.txOperations());

        return new MergerTransactionPlan(actionId, resultEntityId, tx,
                terminatedEntityIds, createdSecurityIds, terminatedSecurityIds, lineageRecords, issuerTransitionPlan);
    }

    private void addEntityTransitions(Set<UUID> sourceEntityIds, UUID actionId, UUID resultEntityId,
                                      Object actionTempId, Object resultEntityTempId, List<Object> tx,
                                      List<UUID> terminatedEntityIds, List<LineageRecordResponse> lineageRecords) {

        for (UUID sourceEntityId : sourceEntityIds) {
            UUID entityLineageId = UUID.randomUUID();
            Object entityLineageTempId = Peer.tempid(":db.part/user");

            tx.add(legalEntityRepository.buildStateTransitionTxMap(sourceEntityId, LegalEntityState.MERGED));
            tx.add(lineageRepository.buildLegalEntityLineageTxMap(
                    Util.list(":legal-entity/id", sourceEntityId),
                    resultEntityTempId,
                    actionTempId,
                    entityLineageTempId,
                    entityLineageId
            ));

            terminatedEntityIds.add(sourceEntityId);
            lineageRecords.add(dtoMapper.toLegalEntityLineageRecordResponse(
                    entityLineageId,
                    actionId,
                    sourceEntityId,
                    resultEntityId
            ));
        }
    }

    private void addSecurityTransitions(List<MergerSecurityMappingRequest> securityMappings, Instant validDate, UUID actionId,
                                        Object resultEntityTempId, Object actionTempId, List<Object> tx,
                                        List<UUID> createdSecurityIds, List<UUID> terminatedSecurityIds, List<LineageRecordResponse> lineageRecords) {

        for (MergerSecurityMappingRequest mapping : securityMappings) {
            UUID resultSecurityId = UUID.randomUUID();
            Object resultSecurityTempId = Peer.tempid(":db.part/user");
            createdSecurityIds.add(resultSecurityId);

            tx.add(securityRepository.buildCreateTxMap(
                    resultSecurityId,
                    mapping.resultSecurity().name(),
                    mapping.resultSecurity().type(),
                    resultEntityTempId,
                    Date.from(validDate),
                    mapping.resultSecurity().isin(),
                    mapping.resultSecurity().maturityDate() == null ? null : Date.from(mapping.resultSecurity().maturityDate()),
                    resultSecurityTempId
            ));

            for (UUID sourceSecurityId : mapping.sourceSecurityIds()) {
                UUID securityLineageId = UUID.randomUUID();
                Object securityLineageTempId = Peer.tempid(":db.part/user");

                tx.add(securityRepository.buildStateTransitionTxMap(sourceSecurityId, SecurityState.MERGED));
                tx.add(lineageRepository.buildSecurityLineageTxMap(
                        Util.list(":security/id", sourceSecurityId),
                        resultSecurityTempId,
                        actionTempId,
                        securityLineageTempId,
                        securityLineageId
                ));

                terminatedSecurityIds.add(sourceSecurityId);
                lineageRecords.add(dtoMapper.toSecurityLineageRecordResponse(
                        securityLineageId,
                        actionId,
                        sourceSecurityId,
                        resultSecurityId
                ));
            }
        }
    }

    private CorporateActionResponse buildMergerResponse(MergerTransactionPlan transactionPlan) {
        LegalEntityResponse createdEntity = dtoMapper.toLegalEntityResponse(
                legalEntityRepository.findById(transactionPlan.resultEntityId())
        );
        List<LegalEntityResponse> terminatedEntities = transactionPlan.terminatedEntityIds().stream()
                .map(legalEntityRepository::findById)
                .map(dtoMapper::toLegalEntityResponse)
                .toList();
        List<SecurityResponse> createdSecurities = transactionPlan.createdSecurityIds().stream()
                .map(securityRepository::findById)
                .map(dtoMapper::toSecurityResponse)
                .toList();
        List<SecurityResponse> terminatedSecurities = transactionPlan.terminatedSecurityIds().stream()
                .map(securityRepository::findById)
                .map(dtoMapper::toSecurityResponse)
                .toList();

        return CorporateActionResponse.builder(
                dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(transactionPlan.actionId()))
        )
                .createdEntities(List.of(createdEntity))
                .terminatedEntities(terminatedEntities)
                .createdSecurities(createdSecurities)
                .terminatedSecurities(terminatedSecurities)
                .lineageRecords(transactionPlan.lineageRecords())
                .issuerUpdates(transactionPlan.issuerTransitionPlan().issuerUpdates())
                .build();
    }

    private record MergerValidation(Set<UUID> sourceEntityIds, List<MergerSecurityMappingRequest> securityMappings,
                                    Map<UUID, Map<String, Object>> mappedSourceSecurities, Map<UUID, Map<String, Object>> allSourceSecurities) {}

    private record MergerTransactionPlan(UUID actionId, UUID resultEntityId,
                                         List<Object> tx, List<UUID> terminatedEntityIds, List<UUID> createdSecurityIds, List<UUID> terminatedSecurityIds, List<LineageRecordResponse> lineageRecords,
                                         IssuerTransitionService.IssuerTransitionPlan issuerTransitionPlan) {}
}
