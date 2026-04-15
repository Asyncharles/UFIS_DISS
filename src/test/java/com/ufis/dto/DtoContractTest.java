package com.ufis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ufis.config.JacksonConfig;
import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityRole;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityRole;
import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import com.ufis.dto.request.CreateSecurityRequest;
import com.ufis.dto.request.MergerRequest;
import com.ufis.dto.request.StockSplitRequest;
import com.ufis.dto.response.CorporateActionRecordResponse;
import com.ufis.dto.response.CorporateActionResponse;
import com.ufis.dto.response.IssuerNameHistoryEntryResponse;
import com.ufis.dto.response.IssuerRefResponse;
import com.ufis.dto.response.IssuerUpdateResponse;
import com.ufis.dto.response.LegalEntityActionListEntryResponse;
import com.ufis.dto.response.LegalEntityLineageEntryResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.LineageRecordResponse;
import com.ufis.dto.response.NameHistoryResponse;
import com.ufis.dto.response.SecurityActionListEntryResponse;
import com.ufis.dto.response.SecurityLineageEntryResponse;
import com.ufis.dto.response.SecurityLineageResponse;
import com.ufis.dto.response.SecurityNameHistoryEntryResponse;
import com.ufis.dto.response.SecurityResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new JacksonConfig().objectMapper();
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createSecurityRequestDeserializesSpecPayload() throws Exception {
        String json = """
                {
                  "name": "Alpha Equity",
                  "type": "EQUITY",
                  "issuerId": "11111111-1111-1111-1111-111111111111",
                  "issueDate": "2015-01-01T00:00:00Z",
                  "isin": "US0001112222",
                  "maturityDate": null
                }
                """;

        CreateSecurityRequest request = OBJECT_MAPPER.readValue(json, CreateSecurityRequest.class);

        assertThat(request.name()).isEqualTo("Alpha Equity");
        assertThat(request.type()).isEqualTo(SecurityType.EQUITY);
        assertThat(request.issuerId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(request.issueDate()).isEqualTo(Instant.parse("2015-01-01T00:00:00Z"));
        assertThat(request.isin()).isEqualTo("US0001112222");
        assertThat(request.maturityDate()).isNull();
    }

    @Test
    void mergerRequestDeserializesNestedSpecPayload() throws Exception {
        String json = """
                {
                  "validDate": "2020-06-15T00:00:00Z",
                  "description": "Company A merges with Company B to form Company AB",
                  "sourceEntityIds": [
                    "11111111-1111-1111-1111-111111111111",
                    "22222222-2222-2222-2222-222222222222"
                  ],
                  "resultEntity": {
                    "name": "Company AB Corp",
                    "type": "COMPANY"
                  },
                  "securityMappings": [
                    {
                      "sourceSecurityIds": [
                        "33333333-3333-3333-3333-333333333333",
                        "44444444-4444-4444-4444-444444444444"
                      ],
                      "resultSecurity": {
                        "name": "AB Corp Equity",
                        "type": "EQUITY",
                        "isin": "US1234567890",
                        "maturityDate": null
                      }
                    }
                  ]
                }
                """;

        MergerRequest request = OBJECT_MAPPER.readValue(json, MergerRequest.class);

        assertThat(request.sourceEntityIds()).hasSize(2);
        assertThat(request.resultEntity().name()).isEqualTo("Company AB Corp");
        assertThat(request.securityMappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.sourceSecurityIds()).hasSize(2);
            assertThat(mapping.resultSecurity().isin()).isEqualTo("US1234567890");
        });
    }

    @Test
    void stockSplitRequestValidationRejectsInvalidSplitRatio() {
        StockSplitRequest request = new StockSplitRequest(
                Instant.parse("2023-09-01T00:00:00Z"),
                "2-for-1 stock split on ZetaCorp Equity",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "2-for-one",
                new com.ufis.dto.request.SecurityDraftRequest("ZetaCorp Equity (post-split)", SecurityType.EQUITY, "US2222222222", null)
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("splitRatio");
    }

    @Test
    void securityResponseSerializesInlineIssuerAndNullMaturityDate() throws Exception {
        SecurityResponse response = new SecurityResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Alpha Equity",
                "US0001112222",
                SecurityType.EQUITY,
                SecurityState.ACTIVE,
                true,
                Instant.parse("2015-01-01T00:00:00Z"),
                null,
                new IssuerRefResponse(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "Alpha Corp"
                )
        );

        JsonNode actual = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));
        JsonNode expected = OBJECT_MAPPER.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "Alpha Equity",
                  "isin": "US0001112222",
                  "type": "EQUITY",
                  "state": "ACTIVE",
                  "active": true,
                  "issueDate": "2015-01-01T00:00:00Z",
                  "maturityDate": null,
                  "issuer": {
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "name": "Alpha Corp"
                  }
                }
                """);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void corporateActionResponseSerializesCommonEnvelope() throws Exception {
        CorporateActionResponse response = new CorporateActionResponse(
                new CorporateActionRecordResponse(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        CorporateActionType.MERGER,
                        Instant.parse("2020-06-15T00:00:00Z"),
                        "Company A merges with Company B to form Company AB",
                        null
                ),
                List.of(new LegalEntityResponse(
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                        "Company AB Corp",
                        LegalEntityType.COMPANY,
                        LegalEntityState.ACTIVE,
                        true,
                        Instant.parse("2020-06-15T00:00:00Z")
                )),
                List.of(new LegalEntityResponse(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        "Company A",
                        LegalEntityType.COMPANY,
                        LegalEntityState.MERGED,
                        false,
                        Instant.parse("2010-01-01T00:00:00Z")
                )),
                List.of(),
                List.of(),
                List.of(new LineageRecordResponse(
                        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        null,
                        null,
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                )),
                List.of(new IssuerUpdateResponse(
                        UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                ))
        );

        JsonNode actual = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

        assertThat(actual.get("corporateAction").get("type").asText()).isEqualTo("MERGER");
        assertThat(actual.get("corporateAction").get("splitRatio").isNull()).isTrue();
        assertThat(actual.get("createdEntities")).hasSize(1);
        assertThat(actual.get("terminatedEntities")).hasSize(1);
        assertThat(actual.get("lineageRecords").get(0).get("childSecurityId").isNull()).isTrue();
        assertThat(actual.get("issuerUpdates").get(0).get("newIssuerId").asText())
                .isEqualTo("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    }

    @Test
    void securityLineageResponseSerializesWorkedExampleStructure() throws Exception {
        SecurityLineageResponse response = new SecurityLineageResponse(
                new SecurityResponse(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "AlphaBeta Equity",
                        null,
                        SecurityType.EQUITY,
                        SecurityState.ACTIVE,
                        true,
                        Instant.parse("2019-06-01T00:00:00Z"),
                        null,
                        new IssuerRefResponse(
                                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                "AlphaBeta Group"
                        )
                ),
                List.of(new SecurityLineageEntryResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "Alpha Tech Equity",
                        null,
                        SecurityType.EQUITY,
                        SecurityState.MERGED,
                        false,
                        Instant.parse("2015-01-01T00:00:00Z"),
                        null,
                        new IssuerRefResponse(
                                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                                "Alpha Technologies"
                        ),
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        CorporateActionType.MERGER,
                        Instant.parse("2019-06-01T00:00:00Z")
                )),
                new LegalEntityResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "AlphaBeta Group",
                        LegalEntityType.COMPANY,
                        LegalEntityState.ACTIVE,
                        true,
                        Instant.parse("2019-06-01T00:00:00Z")
                ),
                List.of(new LegalEntityLineageEntryResponse(
                        UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        "Alpha Technologies",
                        LegalEntityType.COMPANY,
                        LegalEntityState.MERGED,
                        false,
                        Instant.parse("2010-01-01T00:00:00Z"),
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        CorporateActionType.MERGER,
                        Instant.parse("2019-06-01T00:00:00Z")
                )),
                new NameHistoryResponse(
                        List.of(new SecurityNameHistoryEntryResponse(
                                Instant.parse("2017-03-15T00:00:00Z"),
                                "Alpha Equity",
                                "Alpha Tech Equity",
                                null,
                                null,
                                UUID.fromString("66666666-6666-6666-6666-666666666666")
                        )),
                        List.of(new IssuerNameHistoryEntryResponse(
                                Instant.parse("2017-03-15T00:00:00Z"),
                                "Alpha Corp",
                                "Alpha Technologies",
                                UUID.fromString("77777777-7777-7777-7777-777777777777")
                        ))
                ),
                Instant.parse("2019-06-02T00:00:00Z")
        );

        JsonNode actual = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

        assertThat(actual.get("security").get("name").asText()).isEqualTo("AlphaBeta Equity");
        assertThat(actual.get("securityLineage")).hasSize(1);
        assertThat(actual.get("issuerLineage")).hasSize(1);
        assertThat(actual.get("nameHistory").get("security")).hasSize(1);
        assertThat(actual.get("nameHistory").get("issuer")).hasSize(1);
        assertThat(actual.get("resolvedAt").asText()).isEqualTo("2019-06-02T00:00:00Z");
    }

    @Test
    void actionListEntriesSerializeRoleEnums() throws Exception {
        SecurityActionListEntryResponse securityAction = new SecurityActionListEntryResponse(
                new CorporateActionRecordResponse(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        CorporateActionType.NAME_CHANGE,
                        Instant.parse("2023-05-20T00:00:00Z"),
                        "Company Z rebrands to ZetaCorp",
                        null
                ),
                SecurityRole.SUBJECT
        );
        LegalEntityActionListEntryResponse legalEntityAction = new LegalEntityActionListEntryResponse(
                new CorporateActionRecordResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        CorporateActionType.ACQUISITION,
                        Instant.parse("2021-03-01T00:00:00Z"),
                        "Company X acquires Company Y",
                        null
                ),
                LegalEntityRole.ACQUIRER
        );

        JsonNode securityJson = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(securityAction));
        JsonNode legalEntityJson = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(legalEntityAction));

        assertThat(securityJson.get("action").get("type").asText()).isEqualTo("NAME_CHANGE");
        assertThat(legalEntityJson.get("action").get("type").asText()).isEqualTo("ACQUISITION");
        assertThat(securityJson.get("role").asText()).isEqualTo("SUBJECT");
        assertThat(legalEntityJson.get("role").asText()).isEqualTo("ACQUIRER");
    }
}
