package com.ufis.service;

import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.SecurityState;
import com.ufis.dto.response.DummyDataSeedResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.simulator.DataSimulator;
import datomic.Connection;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DummyDataSeedService {
    private static final long BASE_SEED = 42L;
    private static final String DEFAULT_SEARCH_HINT = "Alpha";

    private final Connection connection;
    private final DataSimulator simulator;
    private final LegalEntityRepository legalEntityRepository;
    private final SecurityRepository securityRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final LineageRepository lineageRepository;

    public DummyDataSeedResponse seed(DataSimulator.Tier tier) throws Exception {
        long seed = BASE_SEED
                + legalEntityRepository.findAll().size()
                + securityRepository.findAll().size()
                + corporateActionRepository.findAll().size();

        DataSimulator.GeneratedDataset dataset = simulator.generate(
                new DataSimulator.Config(seed, java.time.LocalDate.of(2000, 1, 1)),
                tier
        );

        seedInitialDataset(dataset);
        List<Instant> auditHints = replayCorporateActions(dataset);

        return new DummyDataSeedResponse(
                dataset.tier(),
                dataset.initialEntities().size(),
                dataset.initialSecurities().size(),
                dataset.corporateActions().size(),
                findSampleEntityId(dataset),
                findSampleSecurityId(dataset),
                dataset.corporateActions().isEmpty() ? null : dataset.corporateActions().getFirst().id(),
                DEFAULT_SEARCH_HINT,
                auditHints
        );
    }

    private UUID findSampleEntityId(DataSimulator.GeneratedDataset dataset) {
        return dataset.corporateActions().stream()
                .filter(action -> !action.createdEntitiesSnapshot().isEmpty())
                .map(action -> action.createdEntitiesSnapshot().getFirst().id())
                .findFirst()
                .orElseGet(() -> dataset.initialEntities().isEmpty() ? null : dataset.initialEntities().getFirst().id());
    }

    private UUID findSampleSecurityId(DataSimulator.GeneratedDataset dataset) {
        if (!dataset.initialSecurities().isEmpty()) {
            int mid = dataset.initialSecurities().size() / 2;
            return dataset.initialSecurities().get(mid).id();
        }

        return dataset.corporateActions().stream()
                .filter(action -> !action.createdSecuritiesSnapshot().isEmpty())
                .map(action -> action.createdSecuritiesSnapshot().getFirst().id())
                .findFirst()
                .orElse(null);
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

    private List<Instant> replayCorporateActions(DataSimulator.GeneratedDataset dataset) throws Exception {
        List<DataSimulator.SimulatedCorporateAction> actions = dataset.corporateActions();
        List<Instant> auditHints = new ArrayList<>();

        if (actions.isEmpty()) {
            return auditHints;
        }

        int batchCount = 3;
        int batchSize = Math.max(1, actions.size() / batchCount);
        int prevBatchIndex = -1;

        for (int i = 0; i < actions.size(); i++) {
            int batchIndex = Math.min(i / batchSize, batchCount - 1);

            if (batchIndex > prevBatchIndex && prevBatchIndex >= 0) {
                auditHints.add(Instant.now());
                Thread.sleep(1500);
            }
            prevBatchIndex = batchIndex;

            replayAction(actions.get(i));
        }

        return auditHints;
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
