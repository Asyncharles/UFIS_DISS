package com.ufis.service.handler;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.SecurityState;
import com.ufis.dto.request.RedemptionRequest;
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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionHandler {
    private final Connection connection;
    private final CorporateActionRepository corporateActionRepository;
    private final SecurityRepository securityRepository;
    private final LineageRepository lineageRepository;
    private final CorporateActionValidator corporateActionValidator;
    private final DtoMapper dtoMapper;

    public CorporateActionResponse handle(RedemptionRequest request) {
        corporateActionValidator.validateSecurityActionType(CorporateActionType.REDEMPTION);
        corporateActionValidator.requireActiveSecurity(request.securityId());
        corporateActionValidator.ensureNoDuplicateSecurityValidDate(request.securityId(), request.validDate());

        UUID actionId = UUID.randomUUID();
        UUID lineageId = UUID.randomUUID();
        Object actionTempId = Peer.tempid(":db.part/user");
        Object lineageTempId = Peer.tempid(":db.part/user");

        List<Object> tx = List.of(
                corporateActionRepository.buildCreateTxMap(actionId,
                        CorporateActionType.REDEMPTION,
                        Date.from(request.validDate()),
                        request.description(),
                        null,
                        actionTempId
                ),
                securityRepository.buildStateTransitionTxMap(request.securityId(), SecurityState.REDEEMED),
                lineageRepository.buildSecurityLineageTxMap(
                        Util.list(":security/id", request.securityId()),
                        null,
                        actionTempId,
                        lineageTempId,
                        lineageId
                )
        );

        try {
            connection.transact(tx).get();
            log.info("Processed REDEMPTION for security={}, actionId={}", request.securityId(), actionId);
            Map<String, Object> terminated = securityRepository.findById(request.securityId());
            SecurityResponse terminatedSecurity = dtoMapper.toSecurityResponse(terminated);
            LineageRecordResponse lineageRecord = dtoMapper.toSecurityLineageRecordResponse(
                    lineageId, actionId, request.securityId(), null
            );

            return CorporateActionResponse.builder(dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(actionId)))
                    .terminatedSecurities(List.of(terminatedSecurity))
                    .lineageRecords(List.of(lineageRecord))
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process REDEMPTION for security={}", request.securityId(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to redeem security", ex);
        }
    }
}
