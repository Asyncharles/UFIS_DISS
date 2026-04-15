package com.ufis.controller;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.DatomicEnumMapper;
import com.ufis.repository.LegalEntityRepository;
import datomic.Connection;
import datomic.Util;
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
class BaseEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private CorporateActionRepository corporateActionRepository;

    @Autowired
    private Connection connection;

    @Test
    void postLegalEntityCreatesEntity() throws Exception {
        mockMvc.perform(post("/legal-entity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alpha Corp",
                                  "type": "COMPANY",
                                  "foundedDate": "2010-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha Corp"))
                .andExpect(jsonPath("$.type").value("COMPANY"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.foundedDate").value("2010-01-01T00:00:00Z"));
    }

    @Test
    void getLegalEntityReturnsEntity() throws Exception {
        UUID id = legalEntityRepository.create(
                "Beta Holdings",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2012-06-01T00:00:00Z"))
        );

        mockMvc.perform(get("/legal-entity/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Beta Holdings"))
                .andExpect(jsonPath("$.type").value("COMPANY"));
    }

    @Test
    void getLegalEntityReturnsNotFoundForMissingId() throws Exception {
        mockMvc.perform(get("/legal-entity/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postSecurityCreatesSecurityWithInlineIssuer() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Issuer Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );

        mockMvc.perform(post("/security")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alpha Equity",
                                  "type": "EQUITY",
                                  "issuerId": "%s",
                                  "issueDate": "2015-01-01T00:00:00Z",
                                  "isin": "US0001112222",
                                  "maturityDate": null
                                }
                                """.formatted(issuerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha Equity"))
                .andExpect(jsonPath("$.type").value("EQUITY"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.issuer.id").value(issuerId.toString()))
                .andExpect(jsonPath("$.issuer.name").value("Issuer Corp"));
    }

    @Test
    void getSecurityReturnsNotFoundForMissingId() throws Exception {
        mockMvc.perform(get("/security/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSecurityReturnsEntity() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Gamma Issuer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );

        String response = mockMvc.perform(post("/security")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Gamma Equity",
                                  "type": "EQUITY",
                                  "issuerId": "%s",
                                  "issueDate": "2015-01-01T00:00:00Z",
                                  "isin": "US9999999999",
                                  "maturityDate": null
                                }
                                """.formatted(issuerId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String securityId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/security/{id}", securityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(securityId))
                .andExpect(jsonPath("$.name").value("Gamma Equity"))
                .andExpect(jsonPath("$.issuer.id").value(issuerId.toString()));
    }

    @Test
    void postSecurityRejectsMissingIssuer() throws Exception {
        mockMvc.perform(post("/security")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Missing Issuer Equity",
                                  "type": "EQUITY",
                                  "issuerId": "%s",
                                  "issueDate": "2015-01-01T00:00:00Z",
                                  "isin": "US0001112222",
                                  "maturityDate": null
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void postSecurityRejectsInactiveIssuer() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Inactive Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );

        connection.transact(Util.list(Util.map(
                ":db/id", Util.list(":legal-entity/id", issuerId),
                ":legal-entity/state", DatomicEnumMapper.toKeyword(LegalEntityState.MERGED),
                ":legal-entity/active", false
        ))).get();

        mockMvc.perform(post("/security")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alpha Equity",
                                  "type": "EQUITY",
                                  "issuerId": "%s",
                                  "issueDate": "2015-01-01T00:00:00Z",
                                  "isin": "US0001112222",
                                  "maturityDate": null
                                }
                                """.formatted(issuerId)))
                .andExpect(status().isConflict());
    }

    @Test
    void postSecurityRejectsBondWithoutMaturityDate() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Bond Issuer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );

        mockMvc.perform(post("/security")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alpha Bond 2025",
                                  "type": "BOND",
                                  "issuerId": "%s",
                                  "issueDate": "2015-01-01T00:00:00Z",
                                  "isin": null,
                                  "maturityDate": null
                                }
                                """.formatted(issuerId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postSecurityRejectsEquityWithMaturityDate() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Equity Issuer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );

        mockMvc.perform(post("/security")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alpha Equity",
                                  "type": "EQUITY",
                                  "issuerId": "%s",
                                  "issueDate": "2015-01-01T00:00:00Z",
                                  "isin": "US0001112222",
                                  "maturityDate": "2025-12-31T00:00:00Z"
                                }
                                """.formatted(issuerId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCorporateActionReturnsAction() throws Exception {
        UUID actionId = corporateActionRepository.create(
                CorporateActionType.MERGER,
                Date.from(Instant.parse("2019-06-01T00:00:00Z")),
                "Alpha merges with Beta",
                null
        );

        mockMvc.perform(get("/corporate-action/{id}", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(actionId.toString()))
                .andExpect(jsonPath("$.type").value("MERGER"))
                .andExpect(jsonPath("$.description").value("Alpha merges with Beta"))
                .andExpect(jsonPath("$.splitRatio").doesNotExist());
    }

    @Test
    void getCorporateActionReturnsNotFoundForMissingId() throws Exception {
        mockMvc.perform(get("/corporate-action/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
