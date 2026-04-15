package com.ufis.service.handler;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.SecurityState;
import com.ufis.dto.request.StockSplitRequest;
import com.ufis.dto.response.CorporateActionResponse;
import com.ufis.dto.response.LineageRecordResponse;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.repository.CorporateActionRepository;
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
public class StockSplitHandler {
    private final Connection connection;
    private final CorporateActionRepository corporateActionRepository;
    private final SecurityRepository securityRepository;
    private final LineageRepository lineageRepository;
    private final CorporateActionValidator corporateActionValidator;
    private final DtoMapper dtoMapper;

    @SuppressWarnings("unchecked")
    public CorporateActionResponse handle(StockSplitRequest request) {
        corporateActionValidator.validateSecurityActionType(CorporateActionType.STOCK_SPLIT);
        Map<String, Object> sourceSecurity = corporateActionValidator.requireActiveSecurity(request.sourceSecurityId());
        corporateActionValidator.validateSecurityFields(request.resultSecurity().type(), request.resultSecurity().maturityDate());
        corporateActionValidator.ensureNoDuplicateSecurityValidDate(request.sourceSecurityId(), request.validDate());

        UUID actionId = UUID.randomUUID();
        UUID resultSecurityId = UUID.randomUUID();
        UUID lineageId = UUID.randomUUID();
        Object actionTempId = Peer.tempid(":db.part/user");
        Object securityTempId = Peer.tempid(":db.part/user");
        Object lineageTempId = Peer.tempid(":db.part/user");

        Map<String, Object> issuer = (Map<String, Object>) sourceSecurity.get("issuer");

        List<Object> tx = new ArrayList<>();
        tx.add(corporateActionRepository.buildCreateTxMap(
                actionId,
                CorporateActionType.STOCK_SPLIT,
                Date.from(request.validDate()),
                request.description(),
                request.splitRatio(),
                actionTempId
        ));
        tx.add(securityRepository.buildStateTransitionTxMap(request.sourceSecurityId(), SecurityState.SPLIT));
        tx.add(securityRepository.buildCreateTxMap(
                resultSecurityId,
                request.resultSecurity().name(),
                request.resultSecurity().type(),
                (UUID) issuer.get("id"),
                Date.from(request.validDate()),
                request.resultSecurity().isin(),
                request.resultSecurity().maturityDate() == null ? null : Date.from(request.resultSecurity().maturityDate()),
                securityTempId
        ));
        tx.add(lineageRepository.buildSecurityLineageTxMap(
                Util.list(":security/id", request.sourceSecurityId()),
                securityTempId,
                actionTempId,
                lineageTempId,
                lineageId
        ));

        try {
            connection.transact(tx).get();
            log.info("Processed STOCK_SPLIT for source={}, result={}, actionId={}", request.sourceSecurityId(), resultSecurityId, actionId);
            SecurityResponse terminated = dtoMapper.toSecurityResponse(securityRepository.findById(request.sourceSecurityId()));
            SecurityResponse created = dtoMapper.toSecurityResponse(securityRepository.findById(resultSecurityId));
            LineageRecordResponse lineageRecord = dtoMapper.toSecurityLineageRecordResponse(
                    lineageId, actionId, request.sourceSecurityId(), resultSecurityId
            );

            return CorporateActionResponse.builder(dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(actionId)))
                    .createdSecurities(List.of(created))
                    .terminatedSecurities(List.of(terminated))
                    .lineageRecords(List.of(lineageRecord))
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process STOCK_SPLIT for source={}", request.sourceSecurityId(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to split security", ex);
        }
    }
}
