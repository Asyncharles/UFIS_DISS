package com.ufis.repository;

import com.ufis.domain.enums.CorporateActionType;
import datomic.Connection;
import datomic.Database;
import datomic.Entity;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Repository
@RequiredArgsConstructor
public class CorporateActionRepository {
    private final Connection connection;

    public Map<Object, Object> buildCreateTxMap(UUID id, CorporateActionType type, Date validDate, String description, String splitRatio, Object tempId) {
        Map<Object, Object> txMap = new HashMap<>();

        txMap.put(":db/id", tempId);
        txMap.put(":corporate-action/id", id);
        txMap.put(":corporate-action/type", DatomicEnumMapper.toKeyword(type));
        txMap.put(":corporate-action/valid-date", validDate);
        txMap.put(":corporate-action/description", description);

        if (splitRatio != null) {
            txMap.put(":corporate-action/split-ratio", splitRatio);
        }

        return txMap;
    }

    public UUID create(CorporateActionType type, Date validDate, String description, String splitRatio)
            throws ExecutionException, InterruptedException {
        UUID id = UUID.randomUUID();
        Object tempId = Peer.tempid(":db.part/user");

        Map<Object, Object> txMap = buildCreateTxMap(id, type, validDate, description, splitRatio, tempId);
        connection.transact(Util.list(txMap)).get();
        return id;
    }

    public Map<String, Object> findById(UUID id) {
        Database db = connection.db();
        return findById(db, id);
    }

    public Map<String, Object> findById(Database db, UUID id) {
        Entity entity = db.entity(Util.list(":corporate-action/id", id));
        if (entity == null || entity.get(":corporate-action/id") == null) {
            return null;
        }
        return entityToMap(entity);
    }

    public List<Map<String, Object>> findAll() {
        return findAll(connection.db());
    }

    public List<Map<String, Object>> findAll(Database db) {
        Collection<List<Object>> results = Peer.q(
                "[:find ?aid :where [?e :corporate-action/id ?aid]]",
                db
        );

        List<Map<String, Object>> actions = new ArrayList<>();

        for (List<Object> row : results) {
            UUID id = (UUID) row.get(0);
            Map<String, Object> action = findById(db, id);
            if (action != null) {
                actions.add(action);
            }
        }

        return actions;
    }

    public List<Map<String, Object>> findAllSortedByDateDesc(Database db, int limit) {
        List<Map<String, Object>> all = findAll(db);
        all.sort(Comparator.comparing((Map<String, Object> m) -> (Date) m.get("validDate")).reversed());

        return all.stream().limit(limit).toList();
    }

    private Map<String, Object> entityToMap(Entity entity) {
        Map<String, Object> result = new HashMap<>();

        result.put("id", entity.get(":corporate-action/id"));
        result.put("type", DatomicEnumMapper.toCorporateActionType(entity.get(":corporate-action/type")));

        result.put("validDate", entity.get(":corporate-action/valid-date"));
        result.put("description", entity.get(":corporate-action/description"));
        result.put("splitRatio", entity.get(":corporate-action/split-ratio"));
        return result;
    }
}
