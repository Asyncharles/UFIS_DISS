package com.ufis.service;

import com.ufis.dto.response.IssuerUpdateResponse;
import com.ufis.repository.SecurityRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class IssuerTransitionServiceTest {

    private final SecurityRepository securityRepository = mock(SecurityRepository.class);
    private final IssuerTransitionService service = new IssuerTransitionService(securityRepository, new DtoMapper());

    @Test
    void buildIssuerTransitionPlanUpdatesOnlySecuritiesWhoseIssuerActuallyChanges() {
        UUID unchangedSecurityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID changedSecurityId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID orphanSecurityId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID oldIssuerId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID newIssuerId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Object newIssuerRef = List.of(":legal-entity/id", newIssuerId);

        Map<String, Object> unchanged = Map.of(
                "id", unchangedSecurityId,
                "issuer", Map.of("id", newIssuerId)
        );
        Map<String, Object> changed = Map.of(
                "id", changedSecurityId,
                "issuer", Map.of("id", oldIssuerId)
        );
        Map<String, Object> orphaned = new HashMap<>();
        orphaned.put("id", orphanSecurityId);
        orphaned.put("issuer", null);

        Map<Object, Object> changedTx = Map.of(":security/id", changedSecurityId, ":security/issuer", newIssuerRef);
        Map<Object, Object> orphanedTx = Map.of(":security/id", orphanSecurityId, ":security/issuer", newIssuerRef);

        when(securityRepository.buildIssuerUpdateTxMap(changedSecurityId, newIssuerRef)).thenReturn(changedTx);
        when(securityRepository.buildIssuerUpdateTxMap(orphanSecurityId, newIssuerRef)).thenReturn(orphanedTx);

        IssuerTransitionService.IssuerTransitionPlan plan = service.buildIssuerTransitionPlan(
                List.of(unchanged, changed, orphaned),
                newIssuerRef,
                newIssuerId
        );

        assertThat(plan.txOperations()).containsExactly(changedTx, orphanedTx);
        assertThat(plan.issuerUpdates()).containsExactly(
                new IssuerUpdateResponse(changedSecurityId, oldIssuerId, newIssuerId),
                new IssuerUpdateResponse(orphanSecurityId, null, newIssuerId)
        );
        verify(securityRepository).buildIssuerUpdateTxMap(changedSecurityId, newIssuerRef);
        verify(securityRepository).buildIssuerUpdateTxMap(orphanSecurityId, newIssuerRef);
        verifyNoMoreInteractions(securityRepository);
    }
}
