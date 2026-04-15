package com.ufis.repository;

import com.ufis.domain.enums.*;
import datomic.Connection;
import datomic.Peer;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests proving all five entity groups can be written and queried.
 * Uses an in-memory Datomic database — no Spring context needed.
 */
class DatomicPersistenceTest {

    private static final String DB_URI = "datomic:mem://ufis-persistence-test";
    private Connection connection;
    private LegalEntityRepository legalEntityRepo;
    private SecurityRepository securityRepo;
    private CorporateActionRepository actionRepo;
    private LineageRepository lineageRepo;

    @BeforeEach
    void setUp() throws Exception {
        Peer.createDatabase(DB_URI);
        connection = Peer.connect(DB_URI);

        // Load schema and enums
        connection.transact(DatomicSchema.allSchema()).get();
        connection.transact(DatomicSchema.allEnums()).get();

        legalEntityRepo = new LegalEntityRepository(connection);
        securityRepo = new SecurityRepository(connection);
        actionRepo = new CorporateActionRepository(connection);
        lineageRepo = new LineageRepository(connection);
    }

    @AfterEach
    void tearDown() {
        Peer.deleteDatabase(DB_URI);
    }

    @Test
    void createAndReadLegalEntity() throws Exception {
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));
        UUID id = legalEntityRepo.create("Alpha Corp", LegalEntityType.COMPANY, founded);

        Map<String, Object> entity = legalEntityRepo.findById(id);
        assertNotNull(entity);
        assertEquals(id, entity.get("id"));
        assertEquals("Alpha Corp", entity.get("name"));
        assertEquals(LegalEntityType.COMPANY, entity.get("type"));
        assertEquals(LegalEntityState.ACTIVE, entity.get("state"));
        assertEquals(true, entity.get("active"));
        assertEquals(founded, entity.get("foundedDate"));
    }

    @Test
    void createAndReadSecurity() throws Exception {
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));
        UUID issuerId = legalEntityRepo.create("Alpha Corp", LegalEntityType.COMPANY, founded);

        Date issueDate = Date.from(Instant.parse("2015-01-01T00:00:00Z"));
        UUID secId = securityRepo.create("Alpha Equity", SecurityType.EQUITY, issuerId,
                issueDate, "US0001112222", null);

        Map<String, Object> sec = securityRepo.findById(secId);
        assertNotNull(sec);
        assertEquals(secId, sec.get("id"));
        assertEquals("Alpha Equity", sec.get("name"));
        assertEquals(SecurityType.EQUITY, sec.get("type"));
        assertEquals(SecurityState.ACTIVE, sec.get("state"));
        assertEquals(true, sec.get("active"));
        assertEquals("US0001112222", sec.get("isin"));
        assertNull(sec.get("maturityDate"));

        // Verify inline issuer
        @SuppressWarnings("unchecked")
        Map<String, Object> issuer = (Map<String, Object>) sec.get("issuer");
        assertNotNull(issuer);
        assertEquals(issuerId, issuer.get("id"));
        assertEquals("Alpha Corp", issuer.get("name"));
    }

    @Test
    void createBondWithMaturityDate() throws Exception {
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));
        UUID issuerId = legalEntityRepo.create("Beta Holdings", LegalEntityType.COMPANY, founded);

        Date issueDate = Date.from(Instant.parse("2015-01-01T00:00:00Z"));
        Date maturity = Date.from(Instant.parse("2025-12-31T00:00:00Z"));
        UUID bondId = securityRepo.create("Beta Bond 2025", SecurityType.BOND, issuerId,
                issueDate, null, maturity);

        Map<String, Object> bond = securityRepo.findById(bondId);
        assertNotNull(bond);
        assertEquals(SecurityType.BOND, bond.get("type"));
        assertEquals(maturity, bond.get("maturityDate"));
        assertNull(bond.get("isin"));
    }

    @Test
    void createAndReadCorporateAction() throws Exception {
        Date validDate = Date.from(Instant.parse("2019-06-01T00:00:00Z"));
        UUID actionId = actionRepo.create(CorporateActionType.MERGER, validDate,
                "Alpha merges with Beta", null);

        Map<String, Object> action = actionRepo.findById(actionId);
        assertNotNull(action);
        assertEquals(actionId, action.get("id"));
        assertEquals(CorporateActionType.MERGER, action.get("type"));
        assertEquals(validDate, action.get("validDate"));
        assertEquals("Alpha merges with Beta", action.get("description"));
        assertNull(action.get("splitRatio"));
    }

    @Test
    void createCorporateActionWithSplitRatio() throws Exception {
        Date validDate = Date.from(Instant.parse("2023-09-01T00:00:00Z"));
        UUID actionId = actionRepo.create(CorporateActionType.STOCK_SPLIT, validDate,
                "2-for-1 stock split", "2:1");

        Map<String, Object> action = actionRepo.findById(actionId);
        assertNotNull(action);
        assertEquals(CorporateActionType.STOCK_SPLIT, action.get("type"));
        assertEquals("2:1", action.get("splitRatio"));
    }

    @Test
    void createAndQuerySecurityLineage() throws Exception {
        // Setup: two securities and an action
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));
        UUID issuerId = legalEntityRepo.create("Corp", LegalEntityType.COMPANY, founded);

        Date issueDate = Date.from(Instant.parse("2015-01-01T00:00:00Z"));
        UUID sec1 = securityRepo.create("Sec A", SecurityType.EQUITY, issuerId, issueDate, null, null);
        UUID sec2 = securityRepo.create("Sec B", SecurityType.EQUITY, issuerId, issueDate, null, null);

        Date validDate = Date.from(Instant.parse("2020-01-01T00:00:00Z"));
        UUID actionId = actionRepo.create(CorporateActionType.MERGER, validDate, "merge", null);

        // Create lineage: sec1 -> sec2
        UUID lineageId = lineageRepo.createSecurityLineage(sec1, sec2, actionId);
        assertNotNull(lineageId);

        // Query by child
        List<Map<String, Object>> byChild = lineageRepo.findSecurityLineageByChild(
                connection.db(), sec2);
        assertEquals(1, byChild.size());
        assertEquals(sec1, byChild.get(0).get("parentId"));

        // Query by parent
        List<Map<String, Object>> byParent = lineageRepo.findSecurityLineageByParent(
                connection.db(), sec1);
        assertEquals(1, byParent.size());
        assertEquals(sec2, byParent.get(0).get("childSecurityId"));
    }

    @Test
    void createTerminalSecurityLineage() throws Exception {
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));
        UUID issuerId = legalEntityRepo.create("Corp", LegalEntityType.COMPANY, founded);

        Date issueDate = Date.from(Instant.parse("2015-01-01T00:00:00Z"));
        UUID secId = securityRepo.create("Bond", SecurityType.BOND, issuerId, issueDate,
                null, Date.from(Instant.parse("2025-12-31T00:00:00Z")));

        Date validDate = Date.from(Instant.parse("2025-12-31T00:00:00Z"));
        UUID actionId = actionRepo.create(CorporateActionType.REDEMPTION, validDate, "redeemed", null);

        // Terminal lineage: child is null
        UUID lineageId = lineageRepo.createSecurityLineage(secId, null, actionId);
        assertNotNull(lineageId);

        // Query by parent — should find the terminal record
        List<Map<String, Object>> byParent = lineageRepo.findSecurityLineageByParent(
                connection.db(), secId);
        assertEquals(1, byParent.size());
        assertNull(byParent.get(0).get("childSecurityId"));
    }

    @Test
    void createAndQueryLegalEntityLineage() throws Exception {
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));
        UUID entityA = legalEntityRepo.create("Entity A", LegalEntityType.COMPANY, founded);
        UUID entityB = legalEntityRepo.create("Entity B", LegalEntityType.COMPANY, founded);

        Date validDate = Date.from(Instant.parse("2020-01-01T00:00:00Z"));
        UUID actionId = actionRepo.create(CorporateActionType.MERGER, validDate, "merge", null);

        // Lineage: A -> B
        UUID lineageId = lineageRepo.createLegalEntityLineage(entityA, entityB, actionId);
        assertNotNull(lineageId);

        // Query by child
        List<Map<String, Object>> byChild = lineageRepo.findEntityLineageByChild(
                connection.db(), entityB);
        assertEquals(1, byChild.size());
        assertEquals(entityA, byChild.get(0).get("parentId"));

        // Query by parent
        List<Map<String, Object>> byParent = lineageRepo.findEntityLineageByParent(
                connection.db(), entityA);
        assertEquals(1, byParent.size());
        assertEquals(entityB, byParent.get(0).get("childEntityId"));
    }

    @Test
    void findSecuritiesByIssuer() throws Exception {
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));
        UUID issuerId = legalEntityRepo.create("Issuer Corp", LegalEntityType.COMPANY, founded);

        Date issueDate = Date.from(Instant.parse("2015-01-01T00:00:00Z"));
        UUID sec1 = securityRepo.create("Equity 1", SecurityType.EQUITY, issuerId, issueDate, null, null);
        UUID sec2 = securityRepo.create("Bond 1", SecurityType.BOND, issuerId, issueDate,
                null, Date.from(Instant.parse("2025-12-31T00:00:00Z")));

        List<Map<String, Object>> securities = securityRepo.findByIssuerId(connection.db(), issuerId);
        assertEquals(2, securities.size());

        Set<UUID> ids = new HashSet<>();
        for (Map<String, Object> s : securities) {
            ids.add((UUID) s.get("id"));
        }
        assertTrue(ids.contains(sec1));
        assertTrue(ids.contains(sec2));
    }

    @Test
    void allEnumTypesWork() throws Exception {
        Date founded = Date.from(Instant.parse("2010-01-01T00:00:00Z"));

        // Test all legal entity types
        for (LegalEntityType type : LegalEntityType.values()) {
            UUID id = legalEntityRepo.create("Test " + type, type, founded);
            Map<String, Object> entity = legalEntityRepo.findById(id);
            assertEquals(type, entity.get("type"));
        }
    }

    @Test
    void nonExistentEntityReturnsNull() {
        assertNull(legalEntityRepo.findById(UUID.randomUUID()));
        assertNull(securityRepo.findById(UUID.randomUUID()));
        assertNull(actionRepo.findById(UUID.randomUUID()));
    }
}
