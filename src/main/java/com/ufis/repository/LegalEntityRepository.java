package com.ufis.repository;

import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
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
public class LegalEntityRepository {
    private final Connection connection;

    public UUID create(String name, LegalEntityType type, Date foundedDate) throws ExecutionException, InterruptedException {
        UUID id = UUID.randomUUID();
        Object tempId = Peer.tempid(":db.part/user");

        @SuppressWarnings("unchecked")
        List<Object> tx = Util.list(buildCreateTxMap(id, name, type, foundedDate, tempId));

        connection.transact(tx).get();
        return id;
    }

    public Map<String, Object> findById(UUID id) {
        Database db = connection.db();
        Entity entity = db.entity(Util.list(":legal-entity/id", id));

        if (entity == null || entity.get(":legal-entity/id") == null) {
            return null;
        }

        return entityToMap(entity);
    }

    public Map<String, Object> findById(Database db, UUID id) {
        Entity entity = db.entity(Util.list(":legal-entity/id", id));

        if (entity == null || entity.get(":legal-entity/id") == null) {
            return null;
        }

        return entityToMap(entity);
    }

    public List<Map<String, Object>> findAll() {
        return findAll(connection.db());
    }

    public List<Map<String, Object>> findAll(Database db) {
        Collection<List<Object>> results = Peer.q("[:find ?eid :where [?e :legal-entity/id ?eid]]", db);

        List<Map<String, Object>> entities = new ArrayList<>();

        for (List<Object> row : results) {
            UUID id = (UUID) row.get(0);
            Map<String, Object> entity = findById(db, id);
            if (entity != null) {
                entities.add(entity);
            }
        }

        return entities;
    }

    public List<Map<String, Object>> findByFilters(Database db, LegalEntityType type, LegalEntityState state) {
        if (type == null && state == null) {
            return findAll(db);
        }

        Collection<List<Object>> results;

        if (type != null && state != null) {
            results = Peer.q(
                    "[:find ?eid :in $ ?type ?state " +
                    " :where [?e :legal-entity/type ?type] " +
                    "        [?e :legal-entity/state ?state] " +
                    "        [?e :legal-entity/id ?eid]]",
                    db, DatomicEnumMapper.toKeyword(type), DatomicEnumMapper.toKeyword(state));
        } else if (type != null) {
            results = Peer.q(
                    "[:find ?eid :in $ ?type " +
                    " :where [?e :legal-entity/type ?type] " +
                    "        [?e :legal-entity/id ?eid]]",
                    db, DatomicEnumMapper.toKeyword(type));
        } else {
            results = Peer.q(
                    "[:find ?eid :in $ ?state " +
                    " :where [?e :legal-entity/state ?state] " +
                    "        [?e :legal-entity/id ?eid]]",
                    db, DatomicEnumMapper.toKeyword(state));
        }

        List<Map<String, Object>> entities = new ArrayList<>();
        for (List<Object> row : results) {
            UUID eid = (UUID) row.get(0);
            Map<String, Object> entity = findById(db, eid);
            if (entity != null) {
                entities.add(entity);
            }
        }

        return entities;
    }

    public Long resolveEntityId(Database db, UUID id) {
        return (Long) db.entid(Util.list(":legal-entity/id", id));
    }

    public Map<Object, Object> buildRenameTxMap(UUID id, String newName) {
        return Util.map(
                ":db/id", Util.list(":legal-entity/id", id),
                ":legal-entity/name", newName
        );
    }

    public Map<Object, Object> buildCreateTxMap(UUID id, String name, LegalEntityType type, Date foundedDate, Object tempId) {
        return Util.map(
                ":db/id", tempId,
                ":legal-entity/id", id,
                ":legal-entity/name", name,
                ":legal-entity/type", DatomicEnumMapper.toKeyword(type),
                ":legal-entity/state", DatomicEnumMapper.toKeyword(LegalEntityState.ACTIVE),
                ":legal-entity/active", true,
                ":legal-entity/founded-date", foundedDate
        );
    }

    public Map<Object, Object> buildStateTransitionTxMap(UUID id, LegalEntityState state) {
        boolean active = state == LegalEntityState.ACTIVE;
        return Util.map(
                ":db/id", Util.list(":legal-entity/id", id),
                ":legal-entity/state", DatomicEnumMapper.toKeyword(state),
                ":legal-entity/active", active
        );
    }

    private Map<String, Object> entityToMap(Entity entity) {
        Map<String, Object> result = new HashMap<>();

        result.put("id", entity.get(":legal-entity/id"));
        result.put("name", entity.get(":legal-entity/name"));
        result.put("type", DatomicEnumMapper.toLegalEntityType(entity.get(":legal-entity/type")));
        result.put("state", DatomicEnumMapper.toLegalEntityState(entity.get(":legal-entity/state")));
        result.put("active", entity.get(":legal-entity/active"));
        result.put("foundedDate", entity.get(":legal-entity/founded-date"));

        return result;
    }
}
