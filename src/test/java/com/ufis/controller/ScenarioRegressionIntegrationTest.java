package com.ufis.controller;

import com.jayway.jsonpath.JsonPath;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityType;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
class ScenarioRegressionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Test
    void sequentialNameChangesResolveExpectedSnapshotsAcrossBoundaries() throws Exception {
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
                "US8888000001",
                null
        );

        postNameChange(entityId, securityId, "2016-01-10T00:00:00Z", "Alpha Technologies", "Alpha Tech Equity", "US8888000002");
        postNameChange(entityId, securityId, "2017-01-10T00:00:00Z", "Alpha Platforms", "Alpha Platform Equity", "US8888000003");
        postNameChange(entityId, securityId, "2018-01-10T00:00:00Z", "Alpha Systems", "Alpha Systems Equity", "US8888000004");

        mockMvc.perform(get("/security/{id}/lineage", securityId)
                        .param("validAt", "2015-12-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Equity"))
                .andExpect(jsonPath("$.security.isin").value("US8888000001"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Corp"))
                .andExpect(jsonPath("$.nameHistory.security").isEmpty())
                .andExpect(jsonPath("$.nameHistory.issuer").isEmpty());

        String midResponse = mockMvc.perform(get("/security/{id}/lineage", securityId)
                        .param("validAt", "2017-06-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Platform Equity"))
                .andExpect(jsonPath("$.security.isin").value("US8888000003"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Platforms"))
                .andExpect(jsonPath("$.nameHistory.security.length()").value(2))
                .andExpect(jsonPath("$.nameHistory.issuer.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> securityHistoryNewNames = JsonPath.read(midResponse, "$.nameHistory.security[*].newName");
        List<String> issuerHistoryNewNames = JsonPath.read(midResponse, "$.nameHistory.issuer[*].newName");
        assertThat(securityHistoryNewNames).containsExactly("Alpha Tech Equity", "Alpha Platform Equity");
        assertThat(issuerHistoryNewNames).containsExactly("Alpha Technologies", "Alpha Platforms");

        mockMvc.perform(get("/security/{id}/lineage", securityId)
                        .param("validAt", "2018-02-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Systems Equity"))
                .andExpect(jsonPath("$.security.isin").value("US8888000004"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Systems"))
                .andExpect(jsonPath("$.nameHistory.security.length()").value(3))
                .andExpect(jsonPath("$.nameHistory.issuer.length()").value(3));
    }

    @Test
    void lineageSnapshotsAreDeterministicAcrossRepeatedQueries() throws Exception {
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
                "US9999000001",
                null
        );
        UUID securityB = securityRepository.create(
                "Beta Equity",
                SecurityType.EQUITY,
                entityB,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US9999000002",
                null
        );

        String mergerResponse = mockMvc.perform(post("/corporate-action/merger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2019-06-01T00:00:00Z",
                                  "description": "Alpha merges with Beta",
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
                                        "isin": "US9999000003",
                                        "maturityDate": null
                                      }
                                    }
                                  ]
                                }
                                """.formatted(entityA, entityB, securityA, securityB)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultSecurityId = JsonPath.read(mergerResponse, "$.createdSecurities[0].id");

        String first = mockMvc.perform(get("/security/{id}/lineage", resultSecurityId)
                        .param("validAt", "2019-06-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(get("/security/{id}/lineage", resultSecurityId)
                        .param("validAt", "2019-06-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void spinOffBranchingLineageResolvesAcrossSecurityAndIssuerChains() throws Exception {
        UUID parentEntityId = legalEntityRepository.create(
                "Parent Holdings",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID parentSecurityId = securityRepository.create(
                "Parent Equity",
                SecurityType.EQUITY,
                parentEntityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US7777000001",
                null
        );

        String spinOffResponse = mockMvc.perform(post("/corporate-action/spin-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2022-01-10T00:00:00Z",
                                  "description": "Parent Holdings spins off CloudCo",
                                  "parentEntityId": "%s",
                                  "newEntity": {
                                    "name": "CloudCo Inc",
                                    "type": "COMPANY"
                                  },
                                  "securityMappings": [
                                    {
                                      "parentSecurityId": "%s",
                                      "newSecurity": {
                                        "name": "CloudCo Equity",
                                        "type": "EQUITY",
                                        "isin": "US7777000002",
                                        "maturityDate": null
                                      }
                                    }
                                  ]
                                }
                                """.formatted(parentEntityId, parentSecurityId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String childEntityId = JsonPath.read(spinOffResponse, "$.createdEntities[0].id");
        String childSecurityId = JsonPath.read(spinOffResponse, "$.createdSecurities[0].id");

        mockMvc.perform(get("/security/{id}/lineage", childSecurityId)
                        .param("validAt", "2022-01-11T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("CloudCo Equity"))
                .andExpect(jsonPath("$.currentIssuer.id").value(childEntityId))
                .andExpect(jsonPath("$.securityLineage.length()").value(1))
                .andExpect(jsonPath("$.securityLineage[0].parentId").value(parentSecurityId.toString()))
                .andExpect(jsonPath("$.issuerLineage.length()").value(1))
                .andExpect(jsonPath("$.issuerLineage[0].parentId").value(parentEntityId.toString()))
                .andExpect(jsonPath("$.resolvedAt").value("2022-01-11T00:00:00Z"));

        mockMvc.perform(get("/legal-entity/{id}/lineage", childEntityId)
                        .param("validAt", "2022-01-11T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntity.name").value("CloudCo Inc"))
                .andExpect(jsonPath("$.issuerLineage.length()").value(1))
                .andExpect(jsonPath("$.issuerLineage[0].parentId").value(parentEntityId.toString()))
                .andExpect(jsonPath("$.resolvedAt").value("2022-01-11T00:00:00Z"));
    }

    private void postNameChange(UUID entityId,
                                UUID securityId,
                                String validDate,
                                String newEntityName,
                                String newSecurityName,
                                String newIsin) throws Exception {
        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "%s",
                                  "description": "Scenario rename",
                                  "entityId": "%s",
                                  "newEntityName": "%s",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "%s",
                                      "newIsin": "%s"
                                    }
                                  ]
                                }
                                """.formatted(validDate, entityId, newEntityName, securityId, newSecurityName, newIsin)))
                .andExpect(status().isCreated());
    }
}
