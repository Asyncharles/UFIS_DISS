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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActionListingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Test
    void securityActionsListReturnsSubjectSourceAndResultRoles() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Alpha Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID sourceSecurityId = securityRepository.create(
                "Alpha Equity",
                SecurityType.EQUITY,
                entityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US7000000001",
                null
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
                                      "newIsin": "US7000000002"
                                    }
                                  ]
                                }
                                """.formatted(entityId, sourceSecurityId)))
                .andExpect(status().isCreated());

        String stockSplitResponse = mockMvc.perform(post("/corporate-action/stock-split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2018-01-10T00:00:00Z",
                                  "description": "Alpha performs a 2:1 split",
                                  "sourceSecurityId": "%s",
                                  "splitRatio": "2:1",
                                  "resultSecurity": {
                                    "name": "Alpha Tech Equity (post-split)",
                                    "type": "EQUITY",
                                    "isin": "US7000000003",
                                    "maturityDate": null
                                  }
                                }
                                """.formatted(sourceSecurityId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultSecurityId = JsonPath.read(stockSplitResponse, "$.createdSecurities[0].id");

        mockMvc.perform(post("/corporate-action/redemption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2019-01-10T00:00:00Z",
                                  "description": "Post-split security is redeemed",
                                  "securityId": "%s"
                                }
                                """.formatted(resultSecurityId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/security/{id}/actions", sourceSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action.type").value("NAME_CHANGE"))
                .andExpect(jsonPath("$[0].role").value("SUBJECT"))
                .andExpect(jsonPath("$[1].action.type").value("STOCK_SPLIT"))
                .andExpect(jsonPath("$[1].role").value("SOURCE"));

        mockMvc.perform(get("/security/{id}/actions", resultSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action.type").value("STOCK_SPLIT"))
                .andExpect(jsonPath("$[0].role").value("RESULT"))
                .andExpect(jsonPath("$[1].action.type").value("REDEMPTION"))
                .andExpect(jsonPath("$[1].role").value("SOURCE"));
    }

    @Test
    void securityActionsListIncludesMergerIssuerRewriteAsSubject() throws Exception {
        UUID entityA = legalEntityRepository.create(
                "Alpha Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID entityB = legalEntityRepository.create(
                "Beta Holdings",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2011-01-01T00:00:00Z"))
        );
        UUID bondId = securityRepository.create(
                "Alpha Bond 2025",
                SecurityType.BOND,
                entityA,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2025-12-31T00:00:00Z"))
        );

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

        mockMvc.perform(get("/security/{id}/actions", bondId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action.type").value("MERGER"))
                .andExpect(jsonPath("$[0].role").value("SUBJECT"));
    }

    @Test
    void legalEntityActionsListReturnsSubjectAcquirerSourceAndResultRoles() throws Exception {
        UUID entityA = legalEntityRepository.create(
                "Alpha Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID entityB = legalEntityRepository.create(
                "Beta Holdings",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2011-01-01T00:00:00Z"))
        );
        UUID targetEntityId = legalEntityRepository.create(
                "Target Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2012-01-01T00:00:00Z"))
        );

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2016-01-01T00:00:00Z",
                                  "description": "Alpha Corp rebrands",
                                  "entityId": "%s",
                                  "newEntityName": "Alpha Technologies",
                                  "securityRenames": []
                                }
                                """.formatted(entityA)))
                .andExpect(status().isCreated());

        String spinOffResponse = mockMvc.perform(post("/corporate-action/spin-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2017-01-01T00:00:00Z",
                                  "description": "Alpha spins off a subsidiary",
                                  "parentEntityId": "%s",
                                  "newEntity": {
                                    "name": "Alpha Ventures",
                                    "type": "COMPANY"
                                  },
                                  "securityMappings": []
                                }
                                """.formatted(entityA)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String spinOffEntityId = JsonPath.read(spinOffResponse, "$.createdEntities[0].id");

        mockMvc.perform(post("/corporate-action/acquisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2018-01-01T00:00:00Z",
                                  "description": "Alpha acquires Target",
                                  "acquirerEntityId": "%s",
                                  "targetEntityId": "%s"
                                }
                                """.formatted(entityA, targetEntityId)))
                .andExpect(status().isCreated());

        String mergerResponse = mockMvc.perform(post("/corporate-action/merger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2019-01-01T00:00:00Z",
                                  "description": "Alpha merges with Beta",
                                  "sourceEntityIds": ["%s", "%s"],
                                  "resultEntity": {
                                    "name": "AlphaBeta Group",
                                    "type": "COMPANY"
                                  }
                                }
                                """.formatted(entityA, entityB)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String mergerResultEntityId = JsonPath.read(mergerResponse, "$.createdEntities[0].id");

        mockMvc.perform(get("/legal-entity/{id}/actions", entityA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].action.type").value("NAME_CHANGE"))
                .andExpect(jsonPath("$[0].role").value("SUBJECT"))
                .andExpect(jsonPath("$[1].action.type").value("SPIN_OFF"))
                .andExpect(jsonPath("$[1].role").value("SUBJECT"))
                .andExpect(jsonPath("$[2].action.type").value("ACQUISITION"))
                .andExpect(jsonPath("$[2].role").value("ACQUIRER"))
                .andExpect(jsonPath("$[3].action.type").value("MERGER"))
                .andExpect(jsonPath("$[3].role").value("SOURCE"));

        mockMvc.perform(get("/legal-entity/{id}/actions", targetEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action.type").value("ACQUISITION"))
                .andExpect(jsonPath("$[0].role").value("SOURCE"));

        mockMvc.perform(get("/legal-entity/{id}/actions", spinOffEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action.type").value("SPIN_OFF"))
                .andExpect(jsonPath("$[0].role").value("RESULT"));

        mockMvc.perform(get("/legal-entity/{id}/actions", mergerResultEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action.type").value("MERGER"))
                .andExpect(jsonPath("$[0].role").value("RESULT"));
    }
}
