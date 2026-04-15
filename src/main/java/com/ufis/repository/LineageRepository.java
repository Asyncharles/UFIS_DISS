package com.ufis.repository;

import datomic.Connection;
import datomic.Database;
import datomic.Peer;
import datomic.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Repository
@RequiredArgsConstructor
public class LineageRepository {

    private final Connection connection;

    public UUID createSecurityLineage(UUID parentSecurityId, UUID childSecurityId, UUID actionId) throws ExecutionException, InterruptedException {
        return createSecurityLineage(parentSecurityId, childSecurityId, actionId, connection);
    }

    public UUID createSecurityLineage(UUID parentSecurityId, UUID childSecurityId, UUID actionId, Connection conn) throws ExecutionException, InterruptedException {
        UUID id = UUID.randomUUID();
        Object tempId = Peer.tempid(":db.part/user");

        Map<Object, Object> txMap = new HashMap<>();
        txMap.put(":db/id", tempId);
        txMap.put(":security-lineage/id", id);
        txMap.put(":security-lineage/parent-security", Util.list(":security/id", parentSecurityId));
        txMap.put(":security-lineage/action", Util.list(":corporate-action/id", actionId));

        if (childSecurityId != null) {
            txMap.put(":security-lineage/child-security", Util.list(":security/id", childSecurityId));
        }

        conn.transact(Util.list(txMap)).get();
        return id;
    }

    public Map<Object, Object> buildSecurityLineageTxMap(UUID parentSecurityId, UUID childSecurityId, UUID actionId, Object tempId) {
        return buildSecurityLineageTxMap(parentSecurityId, childSecurityId, actionId, tempId, UUID.randomUUID());
    }

    public Map<Object, Object> buildSecurityLineageTxMap(UUID parentSecurityId, UUID childSecurityId, UUID actionId, Object tempId, UUID lineageId) {
        Map<Object, Object> txMap = new HashMap<>();

        txMap.put(":db/id", tempId);
        txMap.put(":security-lineage/id", lineageId);
        txMap.put(":security-lineage/parent-security", Util.list(":security/id", parentSecurityId));
        txMap.put(":security-lineage/action", Util.list(":corporate-action/id", actionId));

        if (childSecurityId != null) {
            txMap.put(":security-lineage/child-security", Util.list(":security/id", childSecurityId));
        }

        return txMap;
    }

    public Map<Object, Object> buildSecurityLineageTxMap(Object parentSecurityRef, Object childSecurityRef, Object actionRef, Object tempId, UUID lineageId) {
        Map<Object, Object> txMap = new HashMap<>();

        txMap.put(":db/id", tempId);
        txMap.put(":security-lineage/id", lineageId);
        txMap.put(":security-lineage/parent-security", parentSecurityRef);
        txMap.put(":security-lineage/action", actionRef);

        if (childSecurityRef != null) {
            txMap.put(":security-lineage/child-security", childSecurityRef);
        }

        return txMap;
    }

    public UUID createLegalEntityLineage(UUID parentEntityId, UUID childEntityId, UUID actionId) throws ExecutionException, InterruptedException {
        return createLegalEntityLineage(parentEntityId, childEntityId, actionId, connection);
    }

    public UUID createLegalEntityLineage(UUID parentEntityId, UUID childEntityId, UUID actionId, Connection conn) throws ExecutionException, InterruptedException {
        UUID id = UUID.randomUUID();
        Object tempId = Peer.tempid(":db.part/user");

        Map<Object, Object> txMap = new HashMap<>();
        txMap.put(":db/id", tempId);
        txMap.put(":legal-entity-lineage/id", id);
        txMap.put(":legal-entity-lineage/parent-entity", Util.list(":legal-entity/id", parentEntityId));
        txMap.put(":legal-entity-lineage/action", Util.list(":corporate-action/id", actionId));

        if (childEntityId != null) {
            txMap.put(":legal-entity-lineage/child-entity", Util.list(":legal-entity/id", childEntityId));
        }

        conn.transact(Util.list(txMap)).get();
        return id;
    }

    public Map<Object, Object> buildLegalEntityLineageTxMap(UUID parentEntityId, UUID childEntityId, UUID actionId, Object tempId) {
        return buildLegalEntityLineageTxMap(parentEntityId, childEntityId, actionId, tempId, UUID.randomUUID());
    }

    public Map<Object, Object> buildLegalEntityLineageTxMap(UUID parentEntityId, UUID childEntityId, UUID actionId, Object tempId, UUID lineageId) {
        Map<Object, Object> txMap = new HashMap<>();
        txMap.put(":db/id", tempId);
        txMap.put(":legal-entity-lineage/id", lineageId);
        txMap.put(":legal-entity-lineage/parent-entity", Util.list(":legal-entity/id", parentEntityId));
        txMap.put(":legal-entity-lineage/action", Util.list(":corporate-action/id", actionId));

        if (childEntityId != null) {
            txMap.put(":legal-entity-lineage/child-entity", Util.list(":legal-entity/id", childEntityId));
        }
        return txMap;
    }

    public Map<Object, Object> buildLegalEntityLineageTxMap(Object parentEntityRef, Object childEntityRef, Object actionRef, Object tempId, UUID lineageId) {
        Map<Object, Object> txMap = new HashMap<>();
        txMap.put(":db/id", tempId);
        txMap.put(":legal-entity-lineage/id", lineageId);
        txMap.put(":legal-entity-lineage/parent-entity", parentEntityRef);
        txMap.put(":legal-entity-lineage/action", actionRef);

        if (childEntityRef != null) {
            txMap.put(":legal-entity-lineage/child-entity", childEntityRef);
        }

        return txMap;
    }

    public List<Map<String, Object>> findSecurityLineageByAction(Database db, UUID actionId) {
        Collection<List<Object>> withChild = Peer.q(
                "[:find ?lid ?pid ?cid " +
                " :in $ ?action-id " +
                " :where [?ae :corporate-action/id ?action-id] " +
                "        [?le :security-lineage/action ?ae] " +
                "        [?le :security-lineage/id ?lid] " +
                "        [?le :security-lineage/parent-security ?pe] " +
                "        [?pe :security/id ?pid] " +
                "        [?le :security-lineage/child-security ?ce] " +
                "        [?ce :security/id ?cid]]",
                db, actionId
        );

        Collection<List<Object>> terminal = Peer.q(
                "[:find ?lid ?pid " +
                " :in $ ?action-id " +
                " :where [?ae :corporate-action/id ?action-id] " +
                "        [?le :security-lineage/action ?ae] " +
                "        [?le :security-lineage/id ?lid] " +
                "        [?le :security-lineage/parent-security ?pe] " +
                "        [?pe :security/id ?pid] " +
                "        [(missing? $ ?le :security-lineage/child-security)]]",
                db, actionId
        );

        List<Map<String, Object>> list = new ArrayList<>();

        for (List<Object> row : withChild) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("parentSecurityId", row.get(1));
            m.put("childSecurityId", row.get(2));
            m.put("actionId", actionId);
            list.add(m);
        }

        for (List<Object> row : terminal) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("parentSecurityId", row.get(1));
            m.put("childSecurityId", null);
            m.put("actionId", actionId);
            list.add(m);
        }

        return list;
    }

    public List<Map<String, Object>> findEntityLineageByAction(Database db, UUID actionId) {
        Collection<List<Object>> withChild = Peer.q(
                "[:find ?lid ?pid ?cid " +
                " :in $ ?action-id " +
                " :where [?ae :corporate-action/id ?action-id] " +
                "        [?le :legal-entity-lineage/action ?ae] " +
                "        [?le :legal-entity-lineage/id ?lid] " +
                "        [?le :legal-entity-lineage/parent-entity ?pe] " +
                "        [?pe :legal-entity/id ?pid] " +
                "        [?le :legal-entity-lineage/child-entity ?ce] " +
                "        [?ce :legal-entity/id ?cid]]",
                db, actionId
        );

        Collection<List<Object>> terminal = Peer.q(
                "[:find ?lid ?pid " +
                " :in $ ?action-id " +
                " :where [?ae :corporate-action/id ?action-id] " +
                "        [?le :legal-entity-lineage/action ?ae] " +
                "        [?le :legal-entity-lineage/id ?lid] " +
                "        [?le :legal-entity-lineage/parent-entity ?pe] " +
                "        [?pe :legal-entity/id ?pid] " +
                "        [(missing? $ ?le :legal-entity-lineage/child-entity)]]",
                db, actionId
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (List<Object> row : withChild) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("parentEntityId", row.get(1));
            m.put("childEntityId", row.get(2));
            m.put("actionId", actionId);
            list.add(m);
        }
        for (List<Object> row : terminal) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("parentEntityId", row.get(1));
            m.put("childEntityId", null);
            m.put("actionId", actionId);
            list.add(m);
        }
        return list;
    }

    public List<Map<String, Object>> findSecurityLineageByChild(Database db, UUID childSecurityId) {
        Collection<List<Object>> results = Peer.q(
                "[:find ?lid ?pid ?aid " +
                " :in $ ?child-id " +
                " :where [?ce :security/id ?child-id] " +
                "        [?le :security-lineage/child-security ?ce] " +
                "        [?le :security-lineage/id ?lid] " +
                "        [?le :security-lineage/parent-security ?pe] " +
                "        [?pe :security/id ?pid] " +
                "        [?le :security-lineage/action ?ae] " +
                "        [?ae :corporate-action/id ?aid]]",
                db, childSecurityId
        );

        return toLineageList(results);
    }

    public List<Map<String, Object>> findSecurityLineageByParent(Database db, UUID parentSecurityId) {
        Collection<List<Object>> withChild = Peer.q(
                "[:find ?lid ?cid ?aid " +
                " :in $ ?parent-id " +
                " :where [?pe :security/id ?parent-id] " +
                "        [?le :security-lineage/parent-security ?pe] " +
                "        [?le :security-lineage/id ?lid] " +
                "        [?le :security-lineage/child-security ?ce] " +
                "        [?ce :security/id ?cid] " +
                "        [?le :security-lineage/action ?ae] " +
                "        [?ae :corporate-action/id ?aid]]",
                db, parentSecurityId
        );

        Collection<List<Object>> terminal = Peer.q(
                "[:find ?lid ?aid " +
                " :in $ ?parent-id " +
                " :where [?pe :security/id ?parent-id] " +
                "        [?le :security-lineage/parent-security ?pe] " +
                "        [?le :security-lineage/id ?lid] " +
                "        [?le :security-lineage/action ?ae] " +
                "        [?ae :corporate-action/id ?aid] " +
                "        [(missing? $ ?le :security-lineage/child-security)]]",
                db, parentSecurityId
        );

        List<Map<String, Object>> list = new ArrayList<>();

        for (List<Object> row : withChild) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("childSecurityId", row.get(1));
            m.put("actionId", row.get(2));
            m.put("parentSecurityId", parentSecurityId);
            list.add(m);
        }

        for (List<Object> row : terminal) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("childSecurityId", null);
            m.put("actionId", row.get(1));
            m.put("parentSecurityId", parentSecurityId);
            list.add(m);
        }

        return list;
    }

    public List<Map<String, Object>> findEntityLineageByChild(Database db, UUID childEntityId) {
        Collection<List<Object>> results = Peer.q(
                "[:find ?lid ?pid ?aid " +
                " :in $ ?child-id " +
                " :where [?ce :legal-entity/id ?child-id] " +
                "        [?le :legal-entity-lineage/child-entity ?ce] " +
                "        [?le :legal-entity-lineage/id ?lid] " +
                "        [?le :legal-entity-lineage/parent-entity ?pe] " +
                "        [?pe :legal-entity/id ?pid] " +
                "        [?le :legal-entity-lineage/action ?ae] " +
                "        [?ae :corporate-action/id ?aid]]",
                db, childEntityId
        );

        return toLineageList(results);
    }

    public List<Map<String, Object>> findEntityLineageByParent(Database db, UUID parentEntityId) {
        Collection<List<Object>> withChild = Peer.q(
                "[:find ?lid ?cid ?aid " +
                " :in $ ?parent-id " +
                " :where [?pe :legal-entity/id ?parent-id] " +
                "        [?le :legal-entity-lineage/parent-entity ?pe] " +
                "        [?le :legal-entity-lineage/id ?lid] " +
                "        [?le :legal-entity-lineage/child-entity ?ce] " +
                "        [?ce :legal-entity/id ?cid] " +
                "        [?le :legal-entity-lineage/action ?ae] " +
                "        [?ae :corporate-action/id ?aid]]",
                db, parentEntityId
        );

        Collection<List<Object>> terminal = Peer.q(
                "[:find ?lid ?aid " +
                " :in $ ?parent-id " +
                " :where [?pe :legal-entity/id ?parent-id] " +
                "        [?le :legal-entity-lineage/parent-entity ?pe] " +
                "        [?le :legal-entity-lineage/id ?lid] " +
                "        [?le :legal-entity-lineage/action ?ae] " +
                "        [?ae :corporate-action/id ?aid] " +
                "        [(missing? $ ?le :legal-entity-lineage/child-entity)]]",
                db, parentEntityId
        );

        List<Map<String, Object>> list = new ArrayList<>();

        for (List<Object> row : withChild) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("childEntityId", row.get(1));
            m.put("actionId", row.get(2));
            m.put("parentEntityId", parentEntityId);
            list.add(m);
        }

        for (List<Object> row : terminal) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("childEntityId", null);
            m.put("actionId", row.get(1));
            m.put("parentEntityId", parentEntityId);
            list.add(m);
        }

        return list;
    }

    private List<Map<String, Object>> toLineageList(Collection<List<Object>> results) {
        List<Map<String, Object>> list = new ArrayList<>();

        for (List<Object> row : results) {
            Map<String, Object> m = new HashMap<>();
            m.put("lineageId", row.get(0));
            m.put("parentId", row.get(1));
            m.put("actionId", row.get(2));
            list.add(m);
        }

        return list;
    }
}
