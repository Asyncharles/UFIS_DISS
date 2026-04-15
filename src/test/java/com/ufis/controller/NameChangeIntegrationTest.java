package com.ufis.controller;

import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityType;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.SecurityRepository;
import datomic.Connection;
import datomic.Peer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NameChangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private Connection connection;

    @Test
    void postNameChangeUpdatesEntityAndSecurityNames() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Company Z",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Company Z Equity",
                SecurityType.EQUITY,
                entityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US0000000001",
                null
        );

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2023-05-20T00:00:00Z",
                                  "description": "Company Z rebrands to ZetaCorp",
                                  "entityId": "%s",
                                  "newEntityName": "ZetaCorp",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "ZetaCorp Equity",
                                      "newIsin": "US1111111111"
                                    }
                                  ]
                                }
                                """.formatted(entityId, securityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corporateAction.type").value("NAME_CHANGE"))
                .andExpect(jsonPath("$.createdEntities").isArray())
                .andExpect(jsonPath("$.createdEntities").isEmpty())
                .andExpect(jsonPath("$.lineageRecords").isArray())
                .andExpect(jsonPath("$.lineageRecords").isEmpty())
                .andExpect(jsonPath("$.issuerUpdates").isArray())
                .andExpect(jsonPath("$.issuerUpdates").isEmpty());

        mockMvc.perform(get("/legal-entity/{id}", entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ZetaCorp"));

        mockMvc.perform(get("/security/{id}", securityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ZetaCorp Equity"))
                .andExpect(jsonPath("$.isin").value("US1111111111"));
    }

    @Test
    void postNameChangePreservesHistoricalNameAssertions() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Alpha Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Alpha Equity",
                SecurityType.EQUITY,
                entityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                null
        );

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2023-05-20T00:00:00Z",
                                  "description": "Alpha Corp rebrands",
                                  "entityId": "%s",
                                  "newEntityName": "Alpha Technologies",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "Alpha Tech Equity",
                                      "newIsin": null
                                    }
                                  ]
                                }
                                """.formatted(entityId, securityId)))
                .andExpect(status().isCreated());

        Collection<List<Object>> historicEntityNames = Peer.q(
                "[:find ?name :in $ ?entity-id :where [?e :legal-entity/id ?entity-id] [?e :legal-entity/name ?name _ true]]",
                connection.db().history(),
                entityId
        );
        Collection<List<Object>> historicSecurityNames = Peer.q(
                "[:find ?name :in $ ?security-id :where [?e :security/id ?security-id] [?e :security/name ?name _ true]]",
                connection.db().history(),
                securityId
        );

        assertThat(historicEntityNames)
                .extracting(row -> row.get(0))
                .contains("Alpha Corp", "Alpha Technologies");
        assertThat(historicSecurityNames)
                .extracting(row -> row.get(0))
                .contains("Alpha Equity", "Alpha Tech Equity");
    }

    @Test
    void postNameChangeRejectsInactiveSecurityRename() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Dormant Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Dormant Equity",
                SecurityType.EQUITY,
                entityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                null
        );

        connection.transact(datomic.Util.list(datomic.Util.map(
                ":db/id", datomic.Util.list(":security/id", securityId),
                ":security/state", com.ufis.repository.DatomicEnumMapper.toKeyword(com.ufis.domain.enums.SecurityState.MERGED),
                ":security/active", false
        ))).get();

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2023-05-20T00:00:00Z",
                                  "description": "Dormant Corp rebrands",
                                  "entityId": "%s",
                                  "newEntityName": "Dormant Holdings",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "Dormant Holdings Equity",
                                      "newIsin": null
                                    }
                                  ]
                                }
                                """.formatted(entityId, securityId)))
                .andExpect(status().isConflict());
    }

    @Test
    void postStockSplitRejectsDuplicateValidDateAfterSecurityNameChange() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Delta Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Delta Equity",
                SecurityType.EQUITY,
                entityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US4444444444",
                null
        );

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2023-05-20T00:00:00Z",
                                  "description": "Delta Corp rebrands",
                                  "entityId": "%s",
                                  "newEntityName": "Delta Holdings",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "Delta Holdings Equity",
                                      "newIsin": "US5555555555"
                                    }
                                  ]
                                }
                                """.formatted(entityId, securityId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/corporate-action/stock-split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2023-05-20T00:00:00Z",
                                  "description": "Conflicting split",
                                  "sourceSecurityId": "%s",
                                  "splitRatio": "2:1",
                                  "resultSecurity": {
                                    "name": "Delta Holdings Equity (post-split)",
                                    "type": "EQUITY",
                                    "isin": "US6666666666",
                                    "maturityDate": null
                                  }
                                }
                                """.formatted(securityId)))
                .andExpect(status().isConflict());
    }
}
