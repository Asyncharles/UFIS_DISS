package com.ufis.service.lineage;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.dto.response.IssuerNameHistoryEntryResponse;
import com.ufis.dto.response.LegalEntityLineageEntryResponse;
import com.ufis.dto.response.NameHistoryResponse;
import com.ufis.dto.response.SecurityLineageEntryResponse;
import com.ufis.dto.response.SecurityNameHistoryEntryResponse;
import datomic.Database;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
final class NameHistoryBuilder {
    private final LineageQuerySupport querySupport;

    NameHistoryResponse buildSecurityNameHistory(UUID securityId, List<SecurityLineageEntryResponse> securityLineage, UUID currentIssuerId,
                                                 List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        Database db = querySupport.currentDb();
        return buildSecurityNameHistory(db, db.history(), securityId, securityLineage, currentIssuerId, issuerLineage, validAt);
    }

    NameHistoryResponse buildSecurityNameHistory(Database historyDb, UUID securityId, List<SecurityLineageEntryResponse> securityLineage,
                                                 UUID currentIssuerId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        return buildSecurityNameHistory(querySupport.currentDb(), historyDb, securityId, securityLineage, currentIssuerId, issuerLineage, validAt);
    }

    NameHistoryResponse buildLegalEntityNameHistory(UUID entityId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        Database db = querySupport.currentDb();

        return buildLegalEntityNameHistory(db, db.history(), entityId, issuerLineage, validAt);
    }

    NameHistoryResponse buildLegalEntityNameHistory(Database historyDb, UUID entityId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        return buildLegalEntityNameHistory(querySupport.currentDb(), historyDb, entityId, issuerLineage, validAt);
    }

    private NameHistoryResponse buildSecurityNameHistory(Database db, Database historyDb, UUID securityId,
                                                         List<SecurityLineageEntryResponse> securityLineage, UUID currentIssuerId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        Set<UUID> securityIds = new LinkedHashSet<>();
        securityIds.add(securityId);

        for (SecurityLineageEntryResponse entry : securityLineage) {
            securityIds.add(entry.parentId());
        }

        Set<UUID> entityIds = new LinkedHashSet<>();

        if (currentIssuerId != null) {
            entityIds.add(currentIssuerId);
        }

        for (LegalEntityLineageEntryResponse entry : issuerLineage) {
            entityIds.add(entry.parentId());
        }

        return new NameHistoryResponse(
                buildSecurityNameHistoryEntries(db, historyDb, securityIds, validAt),
                buildIssuerNameHistoryEntries(db, historyDb, entityIds, validAt)
        );
    }

    private NameHistoryResponse buildLegalEntityNameHistory(Database db, Database historyDb, UUID entityId, List<LegalEntityLineageEntryResponse> issuerLineage, Instant validAt) {
        Set<UUID> entityIds = new LinkedHashSet<>();
        entityIds.add(entityId);

        for (LegalEntityLineageEntryResponse entry : issuerLineage) {
            entityIds.add(entry.parentId());
        }

        return new NameHistoryResponse(
                List.of(),
                buildIssuerNameHistoryEntries(db, historyDb, entityIds, validAt)
        );
    }

    private List<SecurityNameHistoryEntryResponse> buildSecurityNameHistoryEntries(Database db, Database historyDb, Set<UUID> securityIds, Instant validAt) {
        List<SortableSecurityNameHistoryEntry> entries = new ArrayList<>();

        for (UUID securityId : securityIds) {
            List<AttributeAssertion> nameAssertions =
                    querySupport.getAttributeHistory(db, historyDb, ":security/id", securityId, ":security/name");
            List<AttributeAssertion> isinAssertions =
                    querySupport.getAttributeHistory(db, historyDb, ":security/id", securityId, ":security/isin");

            String resolvedName = nameAssertions.isEmpty() ? null : (String) nameAssertions.getFirst().value();
            String resolvedIsin = isinAssertions.isEmpty() ? null : (String) isinAssertions.getFirst().value();

            Map<Long, AttributeAssertion> nameByTx = indexByTx(nameAssertions);
            Map<Long, AttributeAssertion> isinByTx = indexByTx(isinAssertions);
            List<ActionInfo> actions = collectNameChangeActions(db, historyDb, nameByTx.keySet(), isinByTx.keySet(), validAt);

            for (ActionInfo action : actions) {
                String previousName = resolvedName;
                String previousIsin = resolvedIsin;
                if (nameByTx.containsKey(action.txId())) {
                    resolvedName = (String) nameByTx.get(action.txId()).value();
                }
                if (isinByTx.containsKey(action.txId())) {
                    resolvedIsin = (String) isinByTx.get(action.txId()).value();
                }
                entries.add(new SortableSecurityNameHistoryEntry(
                        securityId,
                        new SecurityNameHistoryEntryResponse(
                                action.validDate(),
                                previousName,
                                resolvedName,
                                previousIsin,
                                resolvedIsin,
                                action.actionId()
                        )
                ));
            }
        }

        entries.sort(Comparator.comparing(SortableSecurityNameHistoryEntry::validDate)
                .thenComparing(SortableSecurityNameHistoryEntry::securityId)
                .thenComparing(SortableSecurityNameHistoryEntry::actionId));

        return entries.stream()
                .map(SortableSecurityNameHistoryEntry::response)
                .toList();
    }

    private List<IssuerNameHistoryEntryResponse> buildIssuerNameHistoryEntries(Database db, Database historyDb, Set<UUID> entityIds, Instant validAt) {
        List<SortableIssuerNameHistoryEntry> entries = new ArrayList<>();

        for (UUID entityId : entityIds) {
            List<AttributeAssertion> nameAssertions =
                    querySupport.getAttributeHistory(db, historyDb, ":legal-entity/id", entityId, ":legal-entity/name");
            String resolvedName = nameAssertions.isEmpty() ? null : (String) nameAssertions.getFirst().value();
            Map<Long, AttributeAssertion> nameByTx = indexByTx(nameAssertions);
            List<ActionInfo> actions = collectNameChangeActions(db, historyDb, nameByTx.keySet(), Set.of(), validAt);

            for (ActionInfo action : actions) {
                String previousName = resolvedName;

                if (nameByTx.containsKey(action.txId())) {
                    resolvedName = (String) nameByTx.get(action.txId()).value();
                }

                entries.add(new SortableIssuerNameHistoryEntry(
                        entityId,
                        new IssuerNameHistoryEntryResponse(
                                action.validDate(),
                                previousName,
                                resolvedName,
                                action.actionId()
                        )
                ));
            }
        }

        entries.sort(Comparator.comparing(SortableIssuerNameHistoryEntry::validDate)
                .thenComparing(SortableIssuerNameHistoryEntry::entityId)
                .thenComparing(SortableIssuerNameHistoryEntry::actionId));

        return entries.stream()
                .map(SortableIssuerNameHistoryEntry::response)
                .toList();
    }

    private List<ActionInfo> collectNameChangeActions(Database db, Database historyDb, Set<Long> firstTxSet, Set<Long> secondTxSet, Instant validAt) {
        Set<Long> txs = new LinkedHashSet<>();
        txs.addAll(firstTxSet);
        txs.addAll(secondTxSet);

        List<ActionInfo> actions = new ArrayList<>();

        for (Long tx : txs) {
            ActionInfo actionInfo = querySupport.getActionInfoForTx(db, historyDb, tx);
            if (actionInfo == null || actionInfo.actionType() != CorporateActionType.NAME_CHANGE) {
                continue;
            }
            if (validAt != null && actionInfo.validDate() != null && actionInfo.validDate().isAfter(validAt)) {
                continue;
            }
            actions.add(actionInfo);
        }

        actions.sort(LineageQuerySupport.ACTION_ORDER.thenComparing(ActionInfo::txId));
        return actions;
    }

    private Map<Long, AttributeAssertion> indexByTx(List<AttributeAssertion> assertions) {
        Map<Long, AttributeAssertion> indexed = new LinkedHashMap<>();
        for (AttributeAssertion assertion : assertions) {
            indexed.put(assertion.txId(), assertion);
        }
        return indexed;
    }

    private record SortableSecurityNameHistoryEntry(UUID securityId, SecurityNameHistoryEntryResponse response) {
        private Instant validDate() {
            return response.validDate();
        }

        private UUID actionId() {
            return response.actionId();
        }
    }

    private record SortableIssuerNameHistoryEntry(UUID entityId, IssuerNameHistoryEntryResponse response) {
        private Instant validDate() {
            return response.validDate();
        }

        private UUID actionId() {
            return response.actionId();
        }
    }
}
