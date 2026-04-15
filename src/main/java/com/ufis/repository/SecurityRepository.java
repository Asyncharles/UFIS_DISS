package com.ufis.repository;

import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import datomic.Connection;
import datomic.Database;
import datomic.Entity;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Repository
@RequiredArgsConstructor
public class SecurityRepository {
    private final Connection connection;

    public UUID create(String name, SecurityType type, UUID issuerId, Date issueDate,
                       String isin, Date maturityDate)
            throws ExecutionException, InterruptedException {
        UUID id = UUID.randomUUID();
        Object tempId = Peer.tempid(":db.part/user");

        Map<Object, Object> txMap = buildCreateTxMap(id, name, type, issuerId, issueDate, isin, maturityDate, tempId);

        connection.transact(Util.list(txMap)).get();
        return id;
    }

    public Map<String, Object> findById(UUID id) {
        Database db = connection.db();
        return findById(db, id);
    }

    public Map<String, Object> findById(Database db, UUID id) {
        Entity entity = db.entity(Util.list(":security/id", id));

        if (entity == null || entity.get(":security/id") == null) {
            return null;
        }

        return entityToMap(entity);
    }

    public List<Map<String, Object>> findAll() {
        return findAll(connection.db());
    }

    public List<Map<String, Object>> findAll(Database db) {
        Collection<List<Object>> results = Peer.q(
                "[:find ?sid :where [?e :security/id ?sid]]",
                db
        );

        List<Map<String, Object>> securities = new ArrayList<>();

        for (List<Object> row : results) {
            UUID id = (UUID) row.get(0);
            Map<String, Object> security = findById(db, id);
            if (security != null) {
                securities.add(security);
            }
        }

        return securities;
    }

    public List<Map<String, Object>> findByFilters(Database db, SecurityType type, SecurityState state) {
        if (type == null && state == null) {
            return findAll(db);
        }

        Collection<List<Object>> results;

        if (type != null && state != null) {
            results = Peer.q(
                    "[:find ?sid :in $ ?type ?state " +
                    " :where [?e :security/type ?type] " +
                    "        [?e :security/state ?state] " +
                    "        [?e :security/id ?sid]]",
                    db, DatomicEnumMapper.toKeyword(type), DatomicEnumMapper.toKeyword(state));
        } else if (type != null) {
            results = Peer.q(
                    "[:find ?sid :in $ ?type " +
                    " :where [?e :security/type ?type] " +
                    "        [?e :security/id ?sid]]",
                    db, DatomicEnumMapper.toKeyword(type));
        } else {
            results = Peer.q(
                    "[:find ?sid :in $ ?state " +
                    " :where [?e :security/state ?state] " +
                    "        [?e :security/id ?sid]]",
                    db, DatomicEnumMapper.toKeyword(state));
        }

        List<Map<String, Object>> securities = new ArrayList<>();

        for (List<Object> row : results) {
            UUID sid = (UUID) row.get(0);
            Map<String, Object> sec = findById(db, sid);
            if (sec != null) {
                securities.add(sec);
            }
        }

        return securities;
    }

    public Long resolveEntityId(Database db, UUID id) {
        return (Long) db.entid(Util.list(":security/id", id));
    }

    public List<Map<String, Object>> findByIssuerId(Database db, UUID issuerId) {
        Collection<List<Object>> results = Peer.q(
                "[:find ?sid " +
                " :in $ ?issuer-id " +
                " :where [?ie :legal-entity/id ?issuer-id] " +
                "        [?se :security/issuer ?ie] " +
                "        [?se :security/id ?sid]]",
                db, issuerId
        );

        List<Map<String, Object>> securities = new ArrayList<>();

        for (List<Object> row : results) {
            UUID sid = (UUID) row.get(0);
            Map<String, Object> sec = findById(db, sid);
            if (sec != null) {
                securities.add(sec);
            }
        }

        return securities;
    }

    public Map<Object, Object> buildRenameTxMap(UUID id, String newName, String newIsin) {
        Map<Object, Object> txMap = new HashMap<>();

        txMap.put(":db/id", Util.list(":security/id", id));
        txMap.put(":security/name", newName);
        if (newIsin != null) {
            txMap.put(":security/isin", newIsin);
        }

        return txMap;
    }

    public Map<Object, Object> buildCreateTxMap(UUID id, String name, SecurityType type, UUID issuerId,
                                                Date issueDate, String isin, Date maturityDate, Object tempId) {
        return buildCreateTxMap(id, name, type, Util.list(":legal-entity/id", issuerId),
                issueDate, isin, maturityDate, tempId);
    }

    public Map<Object, Object> buildCreateTxMap(UUID id, String name, SecurityType type, Object issuerRef,
                                                Date issueDate, String isin, Date maturityDate, Object tempId) {
        Map<Object, Object> txMap = new HashMap<>();

        txMap.put(":db/id", tempId);
        txMap.put(":security/id", id);
        txMap.put(":security/name", name);
        txMap.put(":security/type", DatomicEnumMapper.toKeyword(type));
        txMap.put(":security/state", DatomicEnumMapper.toKeyword(SecurityState.ACTIVE));
        txMap.put(":security/active", true);
        txMap.put(":security/issuer", issuerRef);
        txMap.put(":security/issue-date", issueDate);

        if (isin != null) {
            txMap.put(":security/isin", isin);
        }
        if (maturityDate != null) {
            txMap.put(":security/maturity-date", maturityDate);
        }

        return txMap;
    }

    public Map<Object, Object> buildStateTransitionTxMap(UUID id, SecurityState state) {
        boolean active = state == SecurityState.ACTIVE;

        Map<Object, Object> txMap = new HashMap<>();

        txMap.put(":db/id", Util.list(":security/id", id));
        txMap.put(":security/state", DatomicEnumMapper.toKeyword(state));
        txMap.put(":security/active", active);

        return txMap;
    }

    public Map<Object, Object> buildIssuerUpdateTxMap(UUID securityId, UUID newIssuerId) {
        return buildIssuerUpdateTxMap(securityId, Util.list(":legal-entity/id", newIssuerId));
    }

    public Map<Object, Object> buildIssuerUpdateTxMap(UUID securityId, Object issuerRef) {
        Map<Object, Object> txMap = new HashMap<>();

        txMap.put(":db/id", Util.list(":security/id", securityId));
        txMap.put(":security/issuer", issuerRef);

        return txMap;
    }

    private Map<String, Object> entityToMap(Entity entity) {
        Map<String, Object> result = new HashMap<>();

        result.put("id", entity.get(":security/id"));
        result.put("name", entity.get(":security/name"));
        result.put("isin", entity.get(":security/isin"));

        result.put("type", DatomicEnumMapper.toSecurityType(entity.get(":security/type")));
        result.put("state", DatomicEnumMapper.toSecurityState(entity.get(":security/state")));

        result.put("active", entity.get(":security/active"));
        result.put("issueDate", entity.get(":security/issue-date"));
        result.put("maturityDate", entity.get(":security/maturity-date"));

        Entity issuerEntity = (Entity) entity.get(":security/issuer");

        if (issuerEntity != null) {
            Map<String, Object> issuer = new HashMap<>();
            issuer.put("id", issuerEntity.get(":legal-entity/id"));
            issuer.put("name", issuerEntity.get(":legal-entity/name"));
            result.put("issuer", issuer);
        }

        return result;
    }
}
