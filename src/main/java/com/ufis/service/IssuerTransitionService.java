package com.ufis.service;

import com.ufis.dto.response.IssuerUpdateResponse;
import com.ufis.repository.SecurityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssuerTransitionService {
    private final SecurityRepository securityRepository;
    private final DtoMapper dtoMapper;

    @SuppressWarnings("unchecked")
    public IssuerTransitionPlan buildIssuerTransitionPlan(List<Map<String, Object>> securities, Object newIssuerRef, UUID newIssuerId) {
        List<Object> txOperations = new ArrayList<>();
        List<IssuerUpdateResponse> issuerUpdates = new ArrayList<>();

        for (Map<String, Object> security : securities) {
            UUID securityId = (UUID) security.get("id");
            Map<String, Object> issuer = (Map<String, Object>) security.get("issuer");
            UUID oldIssuerId = issuer == null ? null : (UUID) issuer.get("id");
            if (Objects.equals(oldIssuerId, newIssuerId)) {
                continue;
            }

            txOperations.add(securityRepository.buildIssuerUpdateTxMap(securityId, newIssuerRef));
            issuerUpdates.add(dtoMapper.toIssuerUpdateResponse(securityId, oldIssuerId, newIssuerId));
        }

        return new IssuerTransitionPlan(txOperations, issuerUpdates);
    }

    public record IssuerTransitionPlan(List<Object> txOperations, List<IssuerUpdateResponse> issuerUpdates) {}
}
