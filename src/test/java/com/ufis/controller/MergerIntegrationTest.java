package com.ufis.controller;

import com.jayway.jsonpath.JsonPath;
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
class MergerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private Connection connection;

    @Test
    void mergerWithoutSecurityMappingsTerminatesSourceEntitiesAndUpdatesIssuers() throws Exception {
        UUID entityA = legalEntityRepository.create(
                "Entity A",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID entityB = legalEntityRepository.create(
                "Entity B",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2012-06-01T00:00:00Z"))
        );
        UUID securityA = securityRepository.create(
                "Entity A Equity",
                SecurityType.EQUITY,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US1212121212",
                null
        );
        UUID securityB = securityRepository.create(
                "Entity B Bond",
                SecurityType.BOND,
                entityB,
                Date.from(Instant.parse("2016-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2028-01-01T00:00:00Z"))
        );

        String response = mockMvc.perform(post("/corporate-action/merger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2020-06-15T00:00:00Z",
                                  "description": "Entity A merges with Entity B",
                                  "sourceEntityIds": ["%s", "%s"],
                                  "resultEntity": {
                                    "name": "Entity AB",
                                    "type": "COMPANY"
                                  }
                                }
                                """.formatted(entityA, entityB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corporateAction.type").value("MERGER"))
                .andExpect(jsonPath("$.createdEntities[0].name").value("Entity AB"))
                .andExpect(jsonPath("$.terminatedEntities.length()").value(2))
                .andExpect(jsonPath("$.createdSecurities").isEmpty())
                .andExpect(jsonPath("$.terminatedSecurities").isEmpty())
                .andExpect(jsonPath("$.lineageRecords.length()").value(2))
                .andExpect(jsonPath("$.issuerUpdates.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultEntityId = JsonPath.read(response, "$.createdEntities[0].id");

        mockMvc.perform(get("/legal-entity/{id}", entityA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MERGED"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/legal-entity/{id}", entityB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MERGED"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/security/{id}", securityA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.issuer.id").value(resultEntityId));

        mockMvc.perform(get("/security/{id}", securityB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.issuer.id").value(resultEntityId));
    }

    @Test
    void mergerWithSecurityMappingsCreatesResultSecurityAndUpdatesOnlySurvivors() throws Exception {
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
        UUID securityA = securityRepository.create(
                "Alpha Equity",
                SecurityType.EQUITY,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US2323232323",
                null
        );
        UUID securityB = securityRepository.create(
                "Beta Equity",
                SecurityType.EQUITY,
                entityB,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US3434343434",
                null
        );
        UUID survivorBond = securityRepository.create(
                "Alpha Bond 2025",
                SecurityType.BOND,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2025-12-31T00:00:00Z"))
        );

        String response = mockMvc.perform(post("/corporate-action/merger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2019-06-01T00:00:00Z",
                                  "description": "Alpha and Beta merge",
                                  "sourceEntityIds": ["%s", "%s"],
                                  "resultEntity": {
                                    "name": "AlphaBeta Group",
                                    "type": "COMPANY"
                                  },
                                  "securityMappings": [
                                    {
                                      "sourceSecurityIds": ["%s", "%s"],
                                      "resultSecurity": {
                                        "name": "AlphaBeta Equity",
                                        "type": "EQUITY",
                                        "isin": "US4545454545",
                                        "maturityDate": null
                                      }
                                    }
                                  ]
                                }
                                """.formatted(entityA, entityB, securityA, securityB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdEntities[0].name").value("AlphaBeta Group"))
                .andExpect(jsonPath("$.terminatedEntities.length()").value(2))
                .andExpect(jsonPath("$.createdSecurities[0].name").value("AlphaBeta Equity"))
                .andExpect(jsonPath("$.terminatedSecurities.length()").value(2))
                .andExpect(jsonPath("$.lineageRecords.length()").value(4))
                .andExpect(jsonPath("$.issuerUpdates.length()").value(1))
                .andExpect(jsonPath("$.issuerUpdates[0].securityId").value(survivorBond.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultEntityId = JsonPath.read(response, "$.createdEntities[0].id");
        String resultSecurityId = JsonPath.read(response, "$.createdSecurities[0].id");

        mockMvc.perform(get("/security/{id}", securityA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MERGED"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/security/{id}", securityB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MERGED"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/security/{id}", survivorBond))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.issuer.id").value(resultEntityId));

        mockMvc.perform(get("/security/{id}", resultSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AlphaBeta Equity"))
                .andExpect(jsonPath("$.issuer.id").value(resultEntityId));
    }

    @Test
    void mergerMatchesAlphaBetaWorkedExample() throws Exception {
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
        UUID security1 = securityRepository.create(
                "Alpha Equity",
                SecurityType.EQUITY,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US5656565656",
                null
        );
        UUID security2 = securityRepository.create(
                "Beta Equity",
                SecurityType.EQUITY,
                entityB,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US6767676767",
                null
        );
        UUID security3 = securityRepository.create(
                "Alpha Bond 2025",
                SecurityType.BOND,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2025-12-31T00:00:00Z"))
        );

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2017-03-15T00:00:00Z",
                                  "description": "Alpha Corp rebrands to Alpha Technologies",
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
                                """.formatted(entityA, security1)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/corporate-action/merger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2019-06-01T00:00:00Z",
                                  "description": "Alpha Corp merges with Beta Holdings to form AlphaBeta Group",
                                  "sourceEntityIds": ["%s", "%s"],
                                  "resultEntity": {
                                    "name": "AlphaBeta Group",
                                    "type": "COMPANY"
                                  },
                                  "securityMappings": [
                                    {
                                      "sourceSecurityIds": ["%s", "%s"],
                                      "resultSecurity": {
                                        "name": "AlphaBeta Equity",
                                        "type": "EQUITY",
                                        "isin": "US1234567890",
                                        "maturityDate": null
                                      }
                                    }
                                  ]
                                }
                                """.formatted(entityA, entityB, security1, security2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdEntities[0].name").value("AlphaBeta Group"))
                .andExpect(jsonPath("$.createdSecurities[0].name").value("AlphaBeta Equity"))
                .andExpect(jsonPath("$.terminatedSecurities.length()").value(2))
                .andExpect(jsonPath("$.issuerUpdates.length()").value(1))
                .andExpect(jsonPath("$.issuerUpdates[0].securityId").value(security3.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultEntityId = JsonPath.read(response, "$.createdEntities[0].id");

        mockMvc.perform(get("/security/{id}", security1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Tech Equity"))
                .andExpect(jsonPath("$.state").value("MERGED"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/security/{id}", security3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Bond 2025"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.issuer.id").value(resultEntityId));
    }

    @Test
    void mergerPreservesOldIssuerInHistoryForSurvivingSecurity() throws Exception {
        UUID entityA = legalEntityRepository.create(
                "History Alpha",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID entityB = legalEntityRepository.create(
                "History Beta",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2012-06-01T00:00:00Z"))
        );
        UUID survivorBond = securityRepository.create(
                "History Alpha Bond",
                SecurityType.BOND,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2025-12-31T00:00:00Z"))
        );

        String response = mockMvc.perform(post("/corporate-action/merger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2019-06-01T00:00:00Z",
                                  "description": "History Alpha merges with History Beta",
                                  "sourceEntityIds": ["%s", "%s"],
                                  "resultEntity": {
                                    "name": "History AlphaBeta",
                                    "type": "COMPANY"
                                  }
                                }
                                """.formatted(entityA, entityB)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultEntityId = JsonPath.read(response, "$.createdEntities[0].id");

        Collection<List<Object>> issuerHistory = Peer.q(
                "[:find ?issuer-id " +
                " :in $ ?security-id " +
                " :where [?se :security/id ?security-id] " +
                "        [?se :security/issuer ?ie _ true] " +
                "        [?ie :legal-entity/id ?issuer-id]]",
                connection.db().history(),
                survivorBond
        );

        assertThat(issuerHistory)
                .extracting(row -> row.get(0))
                .contains(entityA, UUID.fromString(resultEntityId));
    }
}
