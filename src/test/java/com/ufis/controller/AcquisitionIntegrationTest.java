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
class AcquisitionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private Connection connection;

    @Test
    void acquisitionTerminatesTargetAndUpdatesIssuerForItsSecurities() throws Exception {
        UUID acquirerId = legalEntityRepository.create(
                "Company X",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID targetId = legalEntityRepository.create(
                "Company Y",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2011-01-01T00:00:00Z"))
        );
        UUID targetSecurityA = securityRepository.create(
                "Company Y Equity",
                SecurityType.EQUITY,
                targetId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US0000000101",
                null
        );
        UUID targetSecurityB = securityRepository.create(
                "Company Y Bond",
                SecurityType.BOND,
                targetId,
                Date.from(Instant.parse("2016-01-01T00:00:00Z")),
                null,
                Date.from(Instant.parse("2028-01-01T00:00:00Z"))
        );
        UUID acquirerSecurity = securityRepository.create(
                "Company X Equity",
                SecurityType.EQUITY,
                acquirerId,
                Date.from(Instant.parse("2014-01-01T00:00:00Z")),
                "US0000000202",
                null
        );

        mockMvc.perform(post("/corporate-action/acquisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2021-03-01T00:00:00Z",
                                  "description": "Company X acquires Company Y",
                                  "acquirerEntityId": "%s",
                                  "targetEntityId": "%s"
                                }
                                """.formatted(acquirerId, targetId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corporateAction.type").value("ACQUISITION"))
                .andExpect(jsonPath("$.terminatedEntities[0].id").value(targetId.toString()))
                .andExpect(jsonPath("$.terminatedEntities[0].state").value("ACQUIRED"))
                .andExpect(jsonPath("$.terminatedEntities[0].active").value(false))
                .andExpect(jsonPath("$.lineageRecords[0].parentEntityId").value(targetId.toString()))
                .andExpect(jsonPath("$.lineageRecords[0].childEntityId").value(acquirerId.toString()))
                .andExpect(jsonPath("$.createdSecurities").isEmpty())
                .andExpect(jsonPath("$.terminatedSecurities").isEmpty())
                .andExpect(jsonPath("$.issuerUpdates.length()").value(2));

        mockMvc.perform(get("/legal-entity/{id}", targetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACQUIRED"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/security/{id}", targetSecurityA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer.id").value(acquirerId.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        mockMvc.perform(get("/security/{id}", targetSecurityB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer.id").value(acquirerId.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        mockMvc.perform(get("/security/{id}", acquirerSecurity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer.id").value(acquirerId.toString()));
    }

    @Test
    void acquisitionRejectsSameEntityIds() throws Exception {
        UUID entityId = legalEntityRepository.create(
                "Same Corp",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );

        mockMvc.perform(post("/corporate-action/acquisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2021-03-01T00:00:00Z",
                                  "description": "Invalid self acquisition",
                                  "acquirerEntityId": "%s",
                                  "targetEntityId": "%s"
                                }
                                """.formatted(entityId, entityId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acquisitionRejectsDuplicateValidDateForAffectedSecurity() throws Exception {
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
        UUID targetSecurityId = securityRepository.create(
                "Target Equity",
                SecurityType.EQUITY,
                targetId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US7777777777",
                null
        );

        mockMvc.perform(post("/corporate-action/name-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2021-03-01T00:00:00Z",
                                  "description": "Target Corp rebrands",
                                  "entityId": "%s",
                                  "newEntityName": "Target Holdings",
                                  "securityRenames": [
                                    {
                                      "securityId": "%s",
                                      "newName": "Target Holdings Equity",
                                      "newIsin": "US8888888888"
                                    }
                                  ]
                                }
                                """.formatted(targetId, targetSecurityId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/corporate-action/acquisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2021-03-01T00:00:00Z",
                                  "description": "Acquirer Corp acquires Target Holdings",
                                  "acquirerEntityId": "%s",
                                  "targetEntityId": "%s"
                                }
                                """.formatted(acquirerId, targetId)))
                .andExpect(status().isConflict());
    }

    @Test
    void acquisitionPreservesOldIssuerInHistory() throws Exception {
        UUID acquirerId = legalEntityRepository.create(
                "History Acquirer",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2010-01-01T00:00:00Z"))
        );
        UUID targetId = legalEntityRepository.create(
                "History Target",
                LegalEntityType.COMPANY,
                Date.from(Instant.parse("2011-01-01T00:00:00Z"))
        );
        UUID targetSecurityId = securityRepository.create(
                "History Target Equity",
                SecurityType.EQUITY,
                targetId,
                Date.from(Instant.parse("2015-01-01T00:00:00Z")),
                "US1010101010",
                null
        );

        mockMvc.perform(post("/corporate-action/acquisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validDate": "2021-03-01T00:00:00Z",
                                  "description": "History Acquirer acquires History Target",
                                  "acquirerEntityId": "%s",
                                  "targetEntityId": "%s"
                                }
                                """.formatted(acquirerId, targetId)))
                .andExpect(status().isCreated());

        Collection<List<Object>> issuerHistory = Peer.q(
                "[:find ?issuer-id " +
                " :in $ ?security-id " +
                " :where [?se :security/id ?security-id] " +
                "        [?se :security/issuer ?ie _ true] " +
                "        [?ie :legal-entity/id ?issuer-id]]",
                connection.db().history(),
                targetSecurityId
        );

        assertThat(issuerHistory)
                .extracting(row -> row.get(0))
                .contains(targetId, acquirerId);
    }
}
