package com.ufis.controller;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.SecurityState;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.simulator.DataSimulator;
import datomic.Connection;
import datomic.Peer;
import datomic.Util;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimulatorDatasetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Connection connection;

    @Autowired
    private DataSimulator simulator;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private CorporateActionRepository corporateActionRepository;

    @Autowired
    private LineageRepository lineageRepository;

    @Test
    void smallTierSimulatorDatasetSupportsLiveDatomicQueryRoundTrips() throws Exception {
        DataSimulator.GeneratedDataset dataset = simulator.generate(DataSimulator.Tier.SMALL);

        seedInitialDataset(dataset);
        replayCorporateActions(dataset);

        DataSimulator.SimulatedCorporateAction spinOffAction = dataset.corporateActions().stream()
                .filter(action -> action.type() == CorporateActionType.SPIN_OFF)
                .filter(action -> !action.createdEntitiesSnapshot().isEmpty())
                .filter(action -> !action.createdSecuritiesSnapshot().isEmpty())
                .findFirst()
                .orElseThrow();
        DataSimulator.SimulatedCorporateAction redemptionAction = dataset.corporateActions().stream()
                .filter(action -> action.type() == CorporateActionType.REDEMPTION)
                .findFirst()
                .orElseThrow();
        DataSimulator.SimulatedCorporateAction issuerRewriteAction = dataset.corporateActions().stream()
                .filter(action -> !action.issuerUpdatedSecurityIds().isEmpty())
                .findFirst()
                .orElseThrow();

        UUID spunOffEntityId = spinOffAction.createdEntitiesSnapshot().getFirst().id();
        UUID spunOffSecurityId = spinOffAction.createdSecuritiesSnapshot().getFirst().id();
        UUID redeemedSecurityId = redemptionAction.securityIds().getFirst();
        UUID issuerRewriteSecurityId = issuerRewriteAction.issuerUpdatedSecurityIds().getFirst();
        String spinOffResolvedAt = spinOffAction.validDate().plusSeconds(1).toString();
        String redemptionResolvedAt = redemptionAction.validDate().plusSeconds(1).toString();

        mockMvc.perform(get("/security/{id}/lineage", spunOffSecurityId)
                        .param("validAt", spinOffResolvedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.id").value(spunOffSecurityId.toString()))
                .andExpect(jsonPath("$.currentIssuer.id").value(spunOffEntityId.toString()))
                .andExpect(jsonPath("$.securityLineage.length()").value(1))
                .andExpect(jsonPath("$.securityLineage[0].parentId").value(spinOffAction.securityIds().getFirst().toString()))
                .andExpect(jsonPath("$.issuerLineage.length()").value(1))
                .andExpect(jsonPath("$.issuerLineage[0].parentId").value(spinOffAction.subjectEntityId().toString()))
                .andExpect(jsonPath("$.resolvedAt").value(spinOffResolvedAt));

        mockMvc.perform(get("/legal-entity/{id}/lineage", spunOffEntityId)
                        .param("validAt", spinOffResolvedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntity.id").value(spunOffEntityId.toString()))
                .andExpect(jsonPath("$.issuerLineage.length()").value(1))
                .andExpect(jsonPath("$.issuerLineage[0].parentId").value(spinOffAction.subjectEntityId().toString()))
                .andExpect(jsonPath("$.resolvedAt").value(spinOffResolvedAt));

        mockMvc.perform(get("/security/{id}/lineage", redeemedSecurityId)
                        .param("validAt", redemptionResolvedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.id").value(redeemedSecurityId.toString()))
                .andExpect(jsonPath("$.security.state").value("REDEEMED"))
                .andExpect(jsonPath("$.security.active").value(false))
                .andExpect(jsonPath("$.resolvedAt").value(redemptionResolvedAt));

        String actionListJson = mockMvc.perform(get("/security/{id}/actions", issuerRewriteSecurityId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(actionListJson).contains(issuerRewriteAction.id().toString());

        mockMvc.perform(get("/corporate-action/{id}", spinOffAction.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(spinOffAction.id().toString()))
                .andExpect(jsonPath("$.type").value("SPIN_OFF"));
    }

    private void seedInitialDataset(DataSimulator.GeneratedDataset dataset) throws Exception {
        List<Object> tx = new ArrayList<>();
        Map<UUID, Object> entityRefs = new HashMap<>();
        for (DataSimulator.SimulatedLegalEntity entity : dataset.initialEntities()) {
            Object entityRef = Peer.tempid(":db.part/user");
            entityRefs.put(entity.id(), entityRef);
            tx.add(legalEntityRepository.buildCreateTxMap(
                    entity.id(),
                    entity.name(),
                    entity.type(),
                    Date.from(entity.foundedDate()),
                    entityRef
            ));
        }
        for (DataSimulator.SimulatedSecurity security : dataset.initialSecurities()) {
            tx.add(securityRepository.buildCreateTxMap(
                    security.id(),
                    security.name(),
                    security.type(),
                    entityRefs.getOrDefault(security.issuerId(), Util.list(":legal-entity/id", security.issuerId())),
                    Date.from(security.issueDate()),
                    security.isin(),
                    security.maturityDate() == null ? null : Date.from(security.maturityDate()),
                    Peer.tempid(":db.part/user")
            ));
        }
        connection.transact(tx).get();
    }

    private void replayCorporateActions(DataSimulator.GeneratedDataset dataset) throws Exception {
        for (DataSimulator.SimulatedCorporateAction action : dataset.corporateActions()) {
            replayAction(action);
        }
    }

    private void replayAction(DataSimulator.SimulatedCorporateAction action) throws Exception {
        List<Object> tx = new ArrayList<>();
        Object actionTempId = Peer.tempid(":db.part/user");
        tx.add(corporateActionRepository.buildCreateTxMap(
                action.id(),
                action.type(),
                Date.from(action.validDate()),
                action.description(),
                action.splitRatio(),
                actionTempId
        ));

        Object createdEntityRef = null;
        if (!action.createdEntitiesSnapshot().isEmpty()) {
            DataSimulator.SimulatedLegalEntity createdEntity = action.createdEntitiesSnapshot().getFirst();
            createdEntityRef = Peer.tempid(":db.part/user");
            tx.add(legalEntityRepository.buildCreateTxMap(
                    createdEntity.id(),
                    createdEntity.name(),
                    createdEntity.type(),
                    Date.from(createdEntity.foundedDate()),
                    createdEntityRef
            ));
        }

        Object createdSecurityRef = null;
        if (!action.createdSecuritiesSnapshot().isEmpty()) {
            DataSimulator.SimulatedSecurity createdSecurity = action.createdSecuritiesSnapshot().getFirst();
            createdSecurityRef = Peer.tempid(":db.part/user");
            Object issuerRef = !action.createdEntitiesSnapshot().isEmpty()
                    ? createdEntityRef
                    : Util.list(":legal-entity/id", createdSecurity.issuerId());
            tx.add(securityRepository.buildCreateTxMap(
                    createdSecurity.id(),
                    createdSecurity.name(),
                    createdSecurity.type(),
                    issuerRef,
                    Date.from(createdSecurity.issueDate()),
                    createdSecurity.isin(),
                    createdSecurity.maturityDate() == null ? null : Date.from(createdSecurity.maturityDate()),
                    createdSecurityRef
            ));
        }

        switch (action.type()) {
            case NAME_CHANGE -> {
                tx.add(legalEntityRepository.buildRenameTxMap(action.subjectEntityId(), action.newEntityName()));
                for (DataSimulator.SimulatedSecurityRename rename : action.securityRenames()) {
                    tx.add(securityRepository.buildRenameTxMap(rename.securityId(), rename.newName(), rename.newIsin()));
                }
            }
            case ACQUISITION -> {
                tx.add(legalEntityRepository.buildStateTransitionTxMap(action.targetEntityId(), LegalEntityState.ACQUIRED));
                tx.add(lineageRepository.buildLegalEntityLineageTxMap(
                        Util.list(":legal-entity/id", action.targetEntityId()),
                        Util.list(":legal-entity/id", action.acquirerEntityId()),
                        actionTempId,
                        Peer.tempid(":db.part/user"),
                        UUID.randomUUID()
                ));
                for (UUID securityId : action.issuerUpdatedSecurityIds()) {
                    tx.add(securityRepository.buildIssuerUpdateTxMap(securityId, action.acquirerEntityId()));
                }
            }
            case MERGER -> {
                for (UUID entityId : action.entityIds()) {
                    tx.add(legalEntityRepository.buildStateTransitionTxMap(entityId, LegalEntityState.MERGED));
                    tx.add(lineageRepository.buildLegalEntityLineageTxMap(
                            Util.list(":legal-entity/id", entityId),
                            createdEntityRef,
                            actionTempId,
                            Peer.tempid(":db.part/user"),
                            UUID.randomUUID()
                    ));
                }
                for (UUID securityId : action.securityIds()) {
                    tx.add(securityRepository.buildStateTransitionTxMap(securityId, SecurityState.MERGED));
                    tx.add(lineageRepository.buildSecurityLineageTxMap(
                            Util.list(":security/id", securityId),
                            createdSecurityRef,
                            actionTempId,
                            Peer.tempid(":db.part/user"),
                            UUID.randomUUID()
                    ));
                }
                for (UUID securityId : action.issuerUpdatedSecurityIds()) {
                    tx.add(securityRepository.buildIssuerUpdateTxMap(securityId, createdEntityRef));
                }
            }
            case SPIN_OFF -> {
                tx.add(lineageRepository.buildLegalEntityLineageTxMap(
                        Util.list(":legal-entity/id", action.subjectEntityId()),
                        createdEntityRef,
                        actionTempId,
                        Peer.tempid(":db.part/user"),
                        UUID.randomUUID()
                ));
                tx.add(lineageRepository.buildSecurityLineageTxMap(
                        Util.list(":security/id", action.securityIds().getFirst()),
                        createdSecurityRef,
                        actionTempId,
                        Peer.tempid(":db.part/user"),
                        UUID.randomUUID()
                ));
            }
            case STOCK_SPLIT -> {
                UUID sourceSecurityId = action.securityIds().getFirst();
                tx.add(securityRepository.buildStateTransitionTxMap(sourceSecurityId, SecurityState.SPLIT));
                tx.add(lineageRepository.buildSecurityLineageTxMap(
                        Util.list(":security/id", sourceSecurityId),
                        createdSecurityRef,
                        actionTempId,
                        Peer.tempid(":db.part/user"),
                        UUID.randomUUID()
                ));
            }
            case REDEMPTION -> {
                UUID sourceSecurityId = action.securityIds().getFirst();
                tx.add(securityRepository.buildStateTransitionTxMap(sourceSecurityId, SecurityState.REDEEMED));
                tx.add(lineageRepository.buildSecurityLineageTxMap(
                        Util.list(":security/id", sourceSecurityId),
                        null,
                        actionTempId,
                        Peer.tempid(":db.part/user"),
                        UUID.randomUUID()
                ));
            }
        }

        connection.transact(tx).get();
    }
}
