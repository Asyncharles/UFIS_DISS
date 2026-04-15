package com.ufis.service.handler;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.dto.request.NameChangeRequest;
import com.ufis.dto.request.SecurityRenameRequest;
import com.ufis.dto.response.CorporateActionResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DtoMapper;
import com.ufis.validation.CorporateActionValidator;
import datomic.Connection;
import datomic.Peer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NameChangeHandler {
    private final Connection connection;
    private final CorporateActionRepository corporateActionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final SecurityRepository securityRepository;
    private final CorporateActionValidator corporateActionValidator;
    private final DtoMapper dtoMapper;

    public CorporateActionResponse handle(NameChangeRequest request) {
        corporateActionValidator.validateLegalEntityActionType(CorporateActionType.NAME_CHANGE);
        corporateActionValidator.requireActiveLegalEntity(request.entityId());

        List<SecurityRenameRequest> securityRenames = request.securityRenames() == null ? List.of() : request.securityRenames();

        for (SecurityRenameRequest rename : securityRenames) {
            corporateActionValidator.requireActiveSecurity(rename.securityId());
            corporateActionValidator.ensureNoDuplicateSecurityValidDate(rename.securityId(), request.validDate());
        }

        UUID actionId = UUID.randomUUID();
        Object actionTempId = Peer.tempid(":db.part/user");

        List<Object> tx = new ArrayList<>();
        tx.add(corporateActionRepository.buildCreateTxMap(
                actionId,
                CorporateActionType.NAME_CHANGE,
                Date.from(request.validDate()),
                request.description(),
                null,
                actionTempId
        ));
        tx.add(legalEntityRepository.buildRenameTxMap(request.entityId(), request.newEntityName()));
        for (SecurityRenameRequest rename : securityRenames) {
            tx.add(securityRepository.buildRenameTxMap(rename.securityId(), rename.newName(), rename.newIsin()));
        }

        try {
            connection.transact(tx).get();
            log.info("Processed NAME_CHANGE for entity={}, actionId={}", request.entityId(), actionId);
            return CorporateActionResponse.builder(
                    dtoMapper.toCorporateActionRecordResponse(corporateActionRepository.findById(actionId))
            ).build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to apply NAME_CHANGE for entity={}", request.entityId(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to apply name change", ex);
        }
    }
}
