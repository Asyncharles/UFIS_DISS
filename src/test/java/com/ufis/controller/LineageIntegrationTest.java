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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LineageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Test
    void securityLineageReturnsMergedAncestorsAndIssuerLineage() throws Exception {
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
                "US1111000001",
                null
        );
        UUID securityB = securityRepository.create(
                "Beta Equity",
                SecurityType.EQUITY,
                entityB,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US1111000002",
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
                                        "isin": "US1111000003",
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

        String lineageResponse = mockMvc.perform(get("/security/{id}/lineage", resultSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("AlphaBeta Equity"))
                .andExpect(jsonPath("$.currentIssuer.name").value("AlphaBeta Group"))
                .andExpect(jsonPath("$.securityLineage.length()").value(2))
                .andExpect(jsonPath("$.issuerLineage.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> securityLineageNames = JsonPath.read(lineageResponse, "$.securityLineage[*].parentName");
        List<String> issuerLineageNames = JsonPath.read(lineageResponse, "$.issuerLineage[*].parentName");

        assertThat(securityLineageNames).containsExactlyInAnyOrder("Alpha Equity", "Beta Equity");
        assertThat(issuerLineageNames).containsExactlyInAnyOrder("Alpha Corp", "Beta Holdings");
    }

    @Test
    void securityLineageValidAtFiltersFutureIssuerTransition() throws Exception {
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
                "US2222000001",
                null
        );
        UUID bond = securityRepository.create(
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
                                """.formatted(entityA, security1)))
                .andExpect(status().isCreated());

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

        String response = mockMvc.perform(get("/security/{id}/lineage", bond)
                        .param("validAt", "2018-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Bond 2025"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Technologies"))
                .andExpect(jsonPath("$.securityLineage").isEmpty())
                .andExpect(jsonPath("$.issuerLineage").isEmpty())
                .andExpect(jsonPath("$.resolvedAt").value("2018-01-01T00:00:00Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> issuerHistoryNewNames = JsonPath.read(response, "$.nameHistory.issuer[*].newName");
        assertThat(issuerHistoryNewNames).contains("Alpha Technologies");
    }

    @Test
    void legalEntityLineageReturnsAcquisitionAncestor() throws Exception {
        UUID acquirerId = legalEntityRepository.create(
                "Acquirer Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID targetId = legalEntityRepository.create(
                "Target Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2011-01-01T00:00:00Z"))
        );

        mockMvc.perform(post("/corporate-action/acquisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2021-03-01T00:00:00Z",
                                  "description": "Acquirer acquires target",
                                  "acquirerEntityId": "%s",
                                  "targetEntityId": "%s"
                                }
                                """.formatted(acquirerId, targetId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/legal-entity/{id}/lineage", acquirerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntity.name").value("Acquirer Corp"))
                .andExpect(jsonPath("$.issuerLineage.length()").value(1))
                .andExpect(jsonPath("$.issuerLineage[0].parentName").value("Target Corp"));
    }

    @Test
    void legalEntityLineageValidAtOmitsFutureAcquisition() throws Exception {
        UUID acquirerId = legalEntityRepository.create(
                "Acquirer Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID targetId = legalEntityRepository.create(
                "Target Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2011-01-01T00:00:00Z"))
        );

        mockMvc.perform(post("/corporate-action/acquisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2021-03-01T00:00:00Z",
                                  "description": "Acquirer acquires target",
                                  "acquirerEntityId": "%s",
                                  "targetEntityId": "%s"
                                }
                                """.formatted(acquirerId, targetId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/legal-entity/{id}/lineage", acquirerId)
                        .param("validAt", "2020-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntity.name").value("Acquirer Corp"))
                .andExpect(jsonPath("$.issuerLineage").isEmpty())
                .andExpect(jsonPath("$.resolvedAt").value("2020-01-01T00:00:00Z"));
    }

    @Test
    void securityLineageCarriesAncestorNameHistoryFromWorkedExample() throws Exception {
        ScenarioIds ids = createWorkedExampleScenario();

        String response = mockMvc.perform(get("/security/{id}/lineage", ids.resultSecurityId())
                        .param("validAt", "2019-06-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("AlphaBeta Equity"))
                .andExpect(jsonPath("$.currentIssuer.name").value("AlphaBeta Group"))
                .andExpect(jsonPath("$.securityLineage.length()").value(2))
                .andExpect(jsonPath("$.issuerLineage.length()").value(2))
                .andExpect(jsonPath("$.resolvedAt").value("2019-06-02T00:00:00Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> securityLineageNames = JsonPath.read(response, "$.securityLineage[*].parentName");
        List<String> issuerLineageNames = JsonPath.read(response, "$.issuerLineage[*].parentName");
        List<String> securityHistoryNewNames = JsonPath.read(response, "$.nameHistory.security[*].newName");
        List<String> issuerHistoryNewNames = JsonPath.read(response, "$.nameHistory.issuer[*].newName");

        assertThat(securityLineageNames).containsExactlyInAnyOrder("Alpha Tech Equity", "Beta Equity");
        assertThat(issuerLineageNames).containsExactlyInAnyOrder("Alpha Technologies", "Beta Holdings");
        assertThat(securityHistoryNewNames).containsExactly("Alpha Tech Equity");
        assertThat(issuerHistoryNewNames).containsExactly("Alpha Technologies");
    }

    @Test
    void survivingSecurityLineageUsesIssuerChainButNotAncestorSecurityHistory() throws Exception {
        ScenarioIds ids = createWorkedExampleScenario();

        String response = mockMvc.perform(get("/security/{id}/lineage", ids.survivingBondId())
                        .param("validAt", "2019-06-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Bond 2025"))
                .andExpect(jsonPath("$.securityLineage").isEmpty())
                .andExpect(jsonPath("$.currentIssuer.name").value("AlphaBeta Group"))
                .andExpect(jsonPath("$.issuerLineage.length()").value(2))
                .andExpect(jsonPath("$.nameHistory.security").isEmpty())
                .andExpect(jsonPath("$.resolvedAt").value("2019-06-02T00:00:00Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> issuerLineageNames = JsonPath.read(response, "$.issuerLineage[*].parentName");
        List<String> issuerHistoryNewNames = JsonPath.read(response, "$.nameHistory.issuer[*].newName");

        assertThat(issuerLineageNames).containsExactlyInAnyOrder("Alpha Technologies", "Beta Holdings");
        assertThat(issuerHistoryNewNames).containsExactly("Alpha Technologies");
    }

    @Test
    void securityLineageResolvesMutableAttributesBeforeAndAfterNameChangeBoundaries() throws Exception {
        NameChangeScenarioIds ids = createMultiNameChangeScenario();

        mockMvc.perform(get("/security/{id}/lineage", ids.securityId())
                        .param("validAt", "2017-03-14T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Equity"))
                .andExpect(jsonPath("$.security.isin").value("US1000000001"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Corp"))
                .andExpect(jsonPath("$.nameHistory.security").isEmpty())
                .andExpect(jsonPath("$.nameHistory.issuer").isEmpty())
                .andExpect(jsonPath("$.resolvedAt").value("2017-03-14T00:00:00Z"));

        String firstChangeResponse = mockMvc.perform(get("/security/{id}/lineage", ids.securityId())
                        .param("validAt", "2017-03-16T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Tech Equity"))
                .andExpect(jsonPath("$.security.isin").value("US1000000002"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Technologies"))
                .andExpect(jsonPath("$.nameHistory.security.length()").value(1))
                .andExpect(jsonPath("$.nameHistory.issuer.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstPreviousName = JsonPath.read(firstChangeResponse, "$.nameHistory.security[0].previousName");
        String firstNewName = JsonPath.read(firstChangeResponse, "$.nameHistory.security[0].newName");
        String firstPreviousIsin = JsonPath.read(firstChangeResponse, "$.nameHistory.security[0].previousIsin");
        String firstNewIsin = JsonPath.read(firstChangeResponse, "$.nameHistory.security[0].newIsin");
        String firstIssuerPreviousName = JsonPath.read(firstChangeResponse, "$.nameHistory.issuer[0].previousName");
        String firstIssuerNewName = JsonPath.read(firstChangeResponse, "$.nameHistory.issuer[0].newName");

        assertThat(firstPreviousName).isEqualTo("Alpha Equity");
        assertThat(firstNewName).isEqualTo("Alpha Tech Equity");
        assertThat(firstPreviousIsin).isEqualTo("US1000000001");
        assertThat(firstNewIsin).isEqualTo("US1000000002");
        assertThat(firstIssuerPreviousName).isEqualTo("Alpha Corp");
        assertThat(firstIssuerNewName).isEqualTo("Alpha Technologies");

        String latestResponse = mockMvc.perform(get("/security/{id}/lineage", ids.securityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.name").value("Alpha Platform Equity"))
                .andExpect(jsonPath("$.security.isin").value("US1000000003"))
                .andExpect(jsonPath("$.currentIssuer.name").value("Alpha Platforms"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> securityHistoryPreviousNames = JsonPath.read(latestResponse, "$.nameHistory.security[*].previousName");
        List<String> securityHistoryNewNames = JsonPath.read(latestResponse, "$.nameHistory.security[*].newName");
        List<String> securityHistoryPreviousIsins = JsonPath.read(latestResponse, "$.nameHistory.security[*].previousIsin");
        List<String> securityHistoryNewIsins = JsonPath.read(latestResponse, "$.nameHistory.security[*].newIsin");
        List<String> issuerHistoryPreviousNames = JsonPath.read(latestResponse, "$.nameHistory.issuer[*].previousName");
        List<String> issuerHistoryNewNames = JsonPath.read(latestResponse, "$.nameHistory.issuer[*].newName");

        assertThat(securityHistoryPreviousNames).containsExactly("Alpha Equity", "Alpha Tech Equity");
        assertThat(securityHistoryNewNames).containsExactly("Alpha Tech Equity", "Alpha Platform Equity");
        assertThat(securityHistoryPreviousIsins).containsExactly("US1000000001", "US1000000002");
        assertThat(securityHistoryNewIsins).containsExactly("US1000000002", "US1000000003");
        assertThat(issuerHistoryPreviousNames).containsExactly("Alpha Corp", "Alpha Technologies");
        assertThat(issuerHistoryNewNames).containsExactly("Alpha Technologies", "Alpha Platforms");
    }

    @Test
    void legalEntityLineageResolvesNameHistoryAcrossMultipleNameChanges() throws Exception {
        NameChangeScenarioIds ids = createMultiNameChangeScenario();

        String response = mockMvc.perform(get("/legal-entity/{id}/lineage", ids.entityId())
                        .param("validAt", "2018-09-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntity.name").value("Alpha Platforms"))
                .andExpect(jsonPath("$.issuerLineage").isEmpty())
                .andExpect(jsonPath("$.nameHistory.security").isEmpty())
                .andExpect(jsonPath("$.nameHistory.issuer.length()").value(2))
                .andExpect(jsonPath("$.resolvedAt").value("2018-09-02T00:00:00Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> issuerHistoryPreviousNames = JsonPath.read(response, "$.nameHistory.issuer[*].previousName");
        List<String> issuerHistoryNewNames = JsonPath.read(response, "$.nameHistory.issuer[*].newName");

        assertThat(issuerHistoryPreviousNames).containsExactly("Alpha Corp", "Alpha Technologies");
        assertThat(issuerHistoryNewNames).containsExactly("Alpha Technologies", "Alpha Platforms");
    }

    private ScenarioIds createWorkedExampleScenario() throws Exception {
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
                "US3333000001",
                null
        );
        UUID security2 = securityRepository.create(
                "Beta Equity",
                SecurityType.EQUITY,
                entityB,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US3333000002",
                null
        );
        UUID bond = securityRepository.create(
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
                                """.formatted(entityA, security1)))
                .andExpect(status().isCreated());

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
                                        "isin": "US3333000003",
                                        "maturityDate": null
                                      }
                                    }
                                  ]
                                }
                                """.formatted(entityA, entityB, security1, security2)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultSecurityId = JsonPath.read(mergerResponse, "$.createdSecurities[0].id");
        return new ScenarioIds(UUID.fromString(resultSecurityId), bond);
    }

    private NameChangeScenarioIds createMultiNameChangeScenario() throws Exception {
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
                "US1000000001",
                null
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
                                      "newIsin": "US1000000002"
                                    }
                                  ]
                                }
                                """.formatted(entityId, securityId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2018-09-01T00:00:00Z",
                                  "description": "Alpha Technologies rebrands to Alpha Platforms",
                                  "entityId": "%s",
                                  "newEntityName": "Alpha Platforms",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "Alpha Platform Equity",
                                      "newIsin": "US1000000003"
                                    }
                                  ]
                                }
                                """.formatted(entityId, securityId)))
                .andExpect(status().isCreated());

        return new NameChangeScenarioIds(entityId, securityId);
    }

    private record ScenarioIds(UUID resultSecurityId, UUID survivingBondId) {
    }

    private record NameChangeScenarioIds(UUID entityId, UUID securityId) {
    }
}
