package com.ufis.controller;

import com.ufis.dto.response.DummyDataSeedResponse;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DummyDataSeedService;
import com.ufis.simulator.DataSimulator;
import datomic.Connection;
import datomic.Entity;
import datomic.Peer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditSeedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DummyDataSeedService seedService;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private Connection connection;

    @Test
    void seededDataProducesDivergentAuditResults() throws Exception {
        DummyDataSeedResponse result = seedService.seed(DataSimulator.Tier.SMALL);

        assertFalse(result.auditHintTimestamps().isEmpty(), "Need audit hints");

        // Test EVERY security against EVERY hint to find divergence
        List<Map<String, Object>> allSecurities = securityRepository.findAll();
        String validAt = "2025-01-01T00:00:00Z";
        int divergentCount = 0;

        System.out.println("=== Scanning " + allSecurities.size() + " securities across "
                + result.auditHintTimestamps().size() + " audit hints ===\n");

        for (Map<String, Object> sec : allSecurities) {
            UUID secId = (UUID) sec.get("id");
            String name = (String) sec.get("name");

            for (int h = 0; h < result.auditHintTimestamps().size(); h++) {
                String hint = result.auditHintTimestamps().get(h).toString();

                String standardJson = mockMvc.perform(get("/security/{id}/lineage", secId)
                                .param("validAt", validAt))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();

                var auditResult = mockMvc.perform(get("/security/{id}/lineage/audit", secId)
                                .param("validAt", validAt)
                                .param("knownAt", hint))
                        .andReturn();

                // Security might not exist at this knownAt (created in a later batch)
                if (auditResult.getResponse().getStatus() != 200) {
                    // 404 at this knownAt IS divergence — the security didn't exist yet
                    divergentCount++;
                    System.out.println("DIVERGENCE (not yet known): " + name + " (hint " + h + ")");
                    continue;
                }

                String auditJson = auditResult.getResponse().getContentAsString();
                boolean differs = !standardJson.equals(auditJson);
                if (differs) {
                    divergentCount++;
                    System.out.println("DIVERGENCE: " + name + " (hint " + h + ")");
                    System.out.println("  Standard: " + standardJson.substring(0, Math.min(200, standardJson.length())));
                    System.out.println("  Audit:    " + auditJson.substring(0, Math.min(200, auditJson.length())));
                    System.out.println();
                }
            }
        }

        System.out.println("=== Total divergent (security, hint) pairs: " + divergentCount
                + " out of " + (allSecurities.size() * result.auditHintTimestamps().size()) + " ===");

        assertTrue(divergentCount > 0,
                "At least one security should show audit divergence when queried at a partial-knowledge hint");
    }

    @Test
    void transactionTimestampsAreStaggered() throws Exception {
        seedService.seed(DataSimulator.Tier.SMALL);

        // Query all transaction timestamps
        var results = Peer.q(
                "[:find ?tx ?inst :where [?tx :db/txInstant ?inst]]",
                connection.db()
        );

        var sorted = results.stream()
                .map(row -> ((Date) row.get(1)).toInstant())
                .sorted()
                .toList();

        // Find the largest gap
        long maxGapMs = 0;
        int gapCount = 0;
        for (int i = 1; i < sorted.size(); i++) {
            long gap = sorted.get(i).toEpochMilli() - sorted.get(i - 1).toEpochMilli();
            if (gap > 1000) {
                gapCount++;
                maxGapMs = Math.max(maxGapMs, gap);
            }
        }

        System.out.println("Transactions: " + sorted.size());
        System.out.println("Gaps > 1s: " + gapCount);
        System.out.println("Max gap: " + maxGapMs + "ms");

        assertTrue(gapCount >= 2, "Should have at least 2 gaps > 1s (3 batches = 2 boundaries)");
    }

    private Instant currentKnownAt() {
        Entity txEntity = connection.db().entity(Peer.toTx(connection.db().basisT()));
        return ((Date) txEntity.get(":db/txInstant")).toInstant();
    }
}
