package com.ufis.controller;

import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityType;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.SecurityRepository;
import datomic.Connection;
import datomic.Entity;
import datomic.Peer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLineageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private Connection connection;

    @Test
    void auditLineageUsesKnownAtAsTransactionTimeCutoff() throws Exception {
        UUID entityA = legalEntityRepository.create(
                "Alpha Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID entityB = legalEntityRepository.create(
                "Beta Holdings",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2012-06-01T00:00:00Z"))
        );
        UUID bondId = securityRepository.create(
                "Alpha Bond 2025",
                SecurityType.BOND,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2025-12-31T00:00:00Z"))
        );

        Instant beforeMergerKnownAt = currentKnownAt();

        mockMvc.perform(post("/corporate-action/merger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2019-06-01T00:00:00Z",
                                  "description": "Alpha merges with Beta",
                                  "sourceEntityIds": ["%s", "%s"],
                                  "resultEntity": {
                                    "name": "AlphaBeta Group",
                                    "type": "COMPANY"
                                  }
                                }
                                """.formatted(entityA, entityB)))
                .andExpect(status().isCreated());

        Instant afterMergerKnownAt = currentKnownAt();

        mockMvc.perform(get("/security/{id}/lineage/audit", bondId)
                        .param("validAt", "2019-06-02T00:00:00Z")
                        .param("knownAt", beforeMergerKnownAt.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Bond 2025"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Corp"))
                .andExpect(jsonPath("$.issuerLineage").isEmpty())
                .andExpect(jsonPath("$.resolvedAt").value("2019-06-02T00:00:00Z"));

        mockMvc.perform(get("/security/{id}/lineage/audit", bondId)
                        .param("validAt", "2019-06-02T00:00:00Z")
                        .param("knownAt", afterMergerKnownAt.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Bond 2025"))
                .andExpect(jsonPath("$.currentIssuer.name").value("AlphaBeta Group"))
                .andExpect(jsonPath("$.issuerLineage.length()").value(2))
                .andExpect(jsonPath("$.resolvedAt").value("2019-06-02T00:00:00Z"));
    }

    private Instant currentKnownAt() {
        Entity txEntity = connection.db().entity(Peer.toTx(connection.db().basisT()));
        return ((Date) txEntity.get(":db/txInstant")).toInstant();
    }
}
