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
class SpinOffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Test
    void spinOffCreatesNewEntityLineageAndKeepsParentEntityActive() throws Exception {
        UUID parentEntityId = legalEntityRepository.create(
                "Parent Holdings",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );

        String response = mockMvc.perform(post("/corporate-action/spin-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2022-01-10T00:00:00Z",
                                  "description": "Parent Holdings spins off CloudCo",
                                  "parentEntityId": "%s",
                                  "newEntity": {
                                    "name": "CloudCo Inc",
                                    "type": "COMPANY"
                                  }
                                }
                                """.formatted(parentEntityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corporateAction.type").value("SPIN_OFF"))
                .andExpect(jsonPath("$.createdEntities[0].name").value("CloudCo Inc"))
                .andExpect(jsonPath("$.terminatedEntities").isEmpty())
                .andExpect(jsonPath("$.createdSecurities").isEmpty())
                .andExpect(jsonPath("$.lineageRecords[0].parentEntityId").value(parentEntityId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newEntityId = JsonPath.read(response, "$.createdEntities[0].id");

        mockMvc.perform(get("/legal-entity/{id}", parentEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/legal-entity/{id}", newEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CloudCo Inc"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void spinOffCreatesNewSecurityAndLeavesParentSecuritiesUnchanged() throws Exception {
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
                "US1111222233",
                null
        );
        UUID unaffectedSecurityId = securityRepository.create(
                "Parent Bond",
                SecurityType.BOND,
                parentEntityId,
                Date.from(Instant.parse("2016-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2028-01-01T00:00:00Z"))
        );

        String response = mockMvc.perform(post("/corporate-action/spin-off")
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
                                        "isin": "US9999888877",
                                        "maturityDate": null
                                      }
                                    }
                                  ]
                                }
                                """.formatted(parentEntityId, parentSecurityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdEntities[0].name").value("CloudCo Inc"))
                .andExpect(jsonPath("$.createdSecurities[0].name").value("CloudCo Equity"))
                .andExpect(jsonPath("$.createdSecurities[0].issuer.name").value("CloudCo Inc"))
                .andExpect(jsonPath("$.terminatedEntities").isEmpty())
                .andExpect(jsonPath("$.terminatedSecurities").isEmpty())
                .andExpect(jsonPath("$.lineageRecords.length()").value(2))
                .andExpect(jsonPath("$.lineageRecords[1].parentSecurityId").value(parentSecurityId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newEntityId = JsonPath.read(response, "$.createdEntities[0].id");
        String newSecurityId = JsonPath.read(response, "$.createdSecurities[0].id");

        mockMvc.perform(get("/security/{id}", parentSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Parent Equity"))
                .andExpect(jsonPath("$.issuer.id").value(parentEntityId.toString()))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/security/{id}", unaffectedSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Parent Bond"))
                .andExpect(jsonPath("$.issuer.id").value(parentEntityId.toString()))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/security/{id}", newSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CloudCo Equity"))
                .andExpect(jsonPath("$.issuer.id").value(newEntityId));
    }
}
