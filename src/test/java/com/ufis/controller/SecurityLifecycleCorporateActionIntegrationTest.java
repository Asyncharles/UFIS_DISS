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
class SecurityLifecycleCorporateActionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Test
    void redemptionMarksSecurityRedeemedAndCreatesTerminalLineage() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Bond Issuer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Alpha Bond 2025",
                SecurityType.BOND,
                issuerId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2025-12-31T00:00:00Z"))
        );

        mockMvc.perform(post("/corporate-action/redemption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2025-12-31T00:00:00Z",
                                  "description": "Bond AB-2018 reaches maturity and is redeemed",
                                  "securityId": "%s"
                                }
                                """.formatted(securityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corporateAction.type").value("REDEMPTION"))
                .andExpect(jsonPath("$.terminatedSecurities[0].id").value(securityId.toString()))
                .andExpect(jsonPath("$.terminatedSecurities[0].state").value("REDEEMED"))
                .andExpect(jsonPath("$.terminatedSecurities[0].active").value(false))
                .andExpect(jsonPath("$.createdSecurities").isEmpty())
                .andExpect(jsonPath("$.lineageRecords[0].parentSecurityId").value(securityId.toString()))
                .andExpect(jsonPath("$.lineageRecords[0].childSecurityId").isEmpty());

        mockMvc.perform(get("/security/{id}", securityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REDEEMED"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void stockSplitCreatesReplacementSecurityAndTerminatesSource() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Equity Issuer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID sourceSecurityId = securityRepository.create(
                "ZetaCorp Equity",
                SecurityType.EQUITY,
                issuerId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US0000000002",
                null
        );

        String response = mockMvc.perform(post("/corporate-action/stock-split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2023-09-01T00:00:00Z",
                                  "description": "2-for-1 stock split on ZetaCorp Equity",
                                  "sourceSecurityId": "%s",
                                  "splitRatio": "2:1",
                                  "resultSecurity": {
                                    "name": "ZetaCorp Equity (post-split)",
                                    "type": "EQUITY",
                                    "isin": "US2222222222",
                                    "maturityDate": null
                                  }
                                }
                                """.formatted(sourceSecurityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corporateAction.type").value("STOCK_SPLIT"))
                .andExpect(jsonPath("$.corporateAction.splitRatio").value("2:1"))
                .andExpect(jsonPath("$.terminatedSecurities[0].id").value(sourceSecurityId.toString()))
                .andExpect(jsonPath("$.terminatedSecurities[0].state").value("SPLIT"))
                .andExpect(jsonPath("$.createdSecurities[0].name").value("ZetaCorp Equity (post-split)"))
                .andExpect(jsonPath("$.createdSecurities[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.createdSecurities[0].issuer.id").value(issuerId.toString()))
                .andExpect(jsonPath("$.lineageRecords[0].parentSecurityId").value(sourceSecurityId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resultSecurityId = JsonPath.read(response, "$.createdSecurities[0].id");

        mockMvc.perform(get("/security/{id}", sourceSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SPLIT"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/security/{id}", resultSecurityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ZetaCorp Equity (post-split)"))
                .andExpect(jsonPath("$.issuer.id").value(issuerId.toString()));
    }

    @Test
    void stockSplitRejectsInvalidSplitRatio() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Equity Issuer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID sourceSecurityId = securityRepository.create(
                "Invalid Split Equity",
                SecurityType.EQUITY,
                issuerId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                null
        );

        mockMvc.perform(post("/corporate-action/stock-split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2023-09-01T00:00:00Z",
                                  "description": "invalid split",
                                  "sourceSecurityId": "%s",
                                  "splitRatio": "2-for-1",
                                  "resultSecurity": {
                                    "name": "Invalid Split Equity (post-split)",
                                    "type": "EQUITY",
                                    "isin": null,
                                    "maturityDate": null
                                  }
                                }
                                """.formatted(sourceSecurityId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void terminalSecurityRejectsSubsequentAction() throws Exception {
        UUID issuerId = legalEntityRepository.create(
                "Terminal Issuer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID securityId = securityRepository.create(
                "Terminal Bond",
                SecurityType.BOND,
                issuerId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2025-12-31T00:00:00Z"))
        );

        mockMvc.perform(post("/corporate-action/redemption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2025-12-31T00:00:00Z",
                                  "description": "Terminal redemption",
                                  "securityId": "%s"
                                }
                                """.formatted(securityId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2026-01-01T00:00:00Z",
                                  "description": "Should fail after redemption",
                                  "entityId": "%s",
                                  "newEntityName": "Terminal Issuer Holdings",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "Terminal Bond Renamed",
                                      "newIsin": null
                                    }
                                  ]
                                }
                                """.formatted(issuerId, securityId)))
                .andExpect(status().isConflict());
    }
}
