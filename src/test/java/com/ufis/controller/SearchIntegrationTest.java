package com.ufis.controller;

import com.jayway.jsonpath.JsonPath;
import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityType;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private CorporateActionRepository corporateActionRepository;

    @Test
    void searchReturnsGroupedResultsAcrossCoreObjectTypes() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Alpha Technologies",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Alpha Platform Equity",
                SecurityType.EQUITY,
                entityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US1234567890",
                null
        );
        corporateActionRepository.create(
                CorporateActionType.NAME_CHANGE,
                Date.from(Instant.parse("2017-03-15T00:00:00Z")),
                "Alpha Technologies rebrands",
                null
        );

        mockMvc.perform(get("/search").param("q", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securities.length()").value(1))
                .andExpect(jsonPath("$.securities[0].id").value(securityId.toString()))
                .andExpect(jsonPath("$.securities[0].issuerId").value(entityId.toString()))
                .andExpect(jsonPath("$.legalEntities.length()").value(1))
                .andExpect(jsonPath("$.legalEntities[0].id").value(entityId.toString()))
                .andExpect(jsonPath("$.corporateActions.length()").value(1))
                .andExpect(jsonPath("$.corporateActions[0].type").value("NAME_CHANGE"));
    }

    @Test
    void searchFindsSecurityByExactIdentifier() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Beta Holdings",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2011-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Beta Equity",
                SecurityType.EQUITY,
                entityId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US0000000002",
                null
        );

        mockMvc.perform(get("/search").param("q", securityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securities.length()").value(1))
                .andExpect(jsonPath("$.securities[0].id").value(securityId.toString()))
                .andExpect(jsonPath("$.legalEntities").isEmpty())
                .andExpect(jsonPath("$.corporateActions").isEmpty());
    }

    @Test
    void searchRejectsBlankQueries() throws Exception {
        mockMvc.perform(get("/search").param("q", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchFindsCorporateActionByExactIdentifier() throws Exception {
        UUID actionId = corporateActionRepository.create(
                CorporateActionType.MERGER,
                Date.from(Instant.parse("2020-06-15T00:00:00Z")),
                "Alpha merges with Beta",
                null
        );

        String response = mockMvc.perform(get("/search").param("q", actionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateActions.length()").value(1))
                .andExpect(jsonPath("$.corporateActions[0].id").value(actionId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String description = JsonPath.read(response, "$.corporateActions[0].description");
        org.assertj.core.api.Assertions.assertThat(description).isEqualTo("Alpha merges with Beta");
    }
}
