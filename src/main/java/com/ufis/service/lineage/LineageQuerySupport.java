package com.ufis.service.lineage;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.DatomicEnumMapper;
import com.ufis.util.DateUtils;
import datomic.Connection;
import datomic.Database;
import datomic.Entity;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@RequiredArgsConstructor
public final class LineageQuerySupport {
    static final Comparator<ActionInfo> ACTION_ORDER =
            Comparator.comparing(ActionInfo::validDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(ActionInfo::actionId, Comparator.nullsFirst(Comparator.naturalOrder()));

    static final Comparator<ActionInfo> ACTION_VALID_DATE_ORDER =
            Comparator.comparing(ActionInfo::validDate, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final Connection connection;
    private final CorporateActionRepository corporateActionRepository;

    Database currentDb() {
        return connection.db();
    }

    ActionInfo getActionInfo(Database db, UUID actionId) {
        var action = corporateActionRepository.findById(db, actionId);

        if (action == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Corporate action not found");
        }

        return new ActionInfo(actionId, (CorporateActionType) action.get("type"), DateUtils.toInstant((Date) action.get("validDate")), null);
    }

    ActionInfo getActionInfoForTx(Database db, Database historyDb, long txId) {
        Collection<List<Object>> rows = Peer.q(
                "[:find ?aid ?type ?valid-date " +
                        " :in $ ?tx " +
                        " :where [?ca :corporate-action/id ?aid ?tx true] " +
                        "        [?ca :corporate-action/type ?type ?tx true] " +
                        "        [?ca :corporate-action/valid-date ?valid-date ?tx true]]",
                historyDb,
                txId
        );

        if (rows.isEmpty()) {
            return null;
        }

        List<Object> row = rows.iterator().next();

        return new ActionInfo((UUID) row.get(0), DatomicEnumMapper.toCorporateActionType(toRefValue(db, row.get(1))), DateUtils.toInstant((Date) row.get(2)), txId);
    }

    List<AttributeAssertion> getAttributeHistory(Database db, Database historyDb, String lookupAttr, UUID id, String attr) {
        Collection<List<Object>> rows = Peer.q(
                "[:find ?value ?tx " +
                        " :in $ ?lookup-attr ?id ?attr " +
                        " :where [?e ?lookup-attr ?id] " +
                        "        [?e ?attr ?value ?tx true]]",
                historyDb,
                Util.read(lookupAttr),
                id,
                Util.read(attr)
        );

        List<AttributeAssertion> assertions = new ArrayList<>();

        for (List<Object> row : rows) {
            long txId = (Long) row.get(1);
            assertions.add(new AttributeAssertion(row.get(0), txId, getActionInfoForTx(db, historyDb, txId)));
        }

        assertions.sort(Comparator.comparingLong(AttributeAssertion::txId));

        return assertions;
    }

    <T> T resolveAttributeAt(List<AttributeAssertion> assertions, Instant validAt, Function<Object, T> converter, T fallback) {
        AttributeAssertion best = null;

        for (AttributeAssertion assertion : assertions) {
            ActionInfo actionInfo = assertion.actionInfo();
            if (actionInfo != null && actionInfo.validDate() != null && actionInfo.validDate().isAfter(validAt)) {
                continue;
            }
            if (best == null || compareAssertions(assertion, best) > 0) {
                best = assertion;
            }
        }

        return best == null ? fallback : converter.apply(best.value());
    }

    Object toRefValue(Database db, Object value) {
        if (value instanceof Long entityId) {
            return db.entity(entityId);
        }

        return value;
    }

    Entity toEntityRef(Database db, Object value) {
        Object refValue = toRefValue(db, value);

        return refValue instanceof Entity entity ? entity : null;
    }

    private int compareAssertions(AttributeAssertion left, AttributeAssertion right) {
        Instant leftDate = left.actionInfo() == null || left.actionInfo().validDate() == null
                ? Instant.MIN
                : left.actionInfo().validDate(),
                rightDate = right.actionInfo() == null || right.actionInfo().validDate() == null
                ? Instant.MIN
                : right.actionInfo().validDate();

        int dateCompare = leftDate.compareTo(rightDate);

        if (dateCompare != 0) {
            return dateCompare;
        }

        return Long.compare(left.txId(), right.txId());
    }
}

record ActionInfo(UUID actionId, CorporateActionType actionType, Instant validDate, Long txId) {}

record AttributeAssertion(Object value, long txId, ActionInfo actionInfo) {}
