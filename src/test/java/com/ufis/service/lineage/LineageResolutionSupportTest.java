package com.ufis.service.lineage;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityType;
import com.ufis.dto.response.SecurityLineageEntryResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.DatomicSchema;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DtoMapper;
import datomic.Connection;
import datomic.Peer;
import datomic.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LineageResolutionSupportTest {

    private static final String DB_URI = "datomic:mem://ufis-lineage-resolution-support-test";

    private static final UUID ISSUER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PARENT_SMALL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARENT_LARGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static final UUID LARGE_PARENT_LINEAGE_ACTION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SMALL_PARENT_LINEAGE_ACTION_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID LARGE_PARENT_RENAME_ACTION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SMALL_PARENT_RENAME_ACTION_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private static final Instant ISSUE_DATE = Instant.parse("2015-01-01T00:00:00Z");
    private static final Instant SAME_DAY = Instant.parse("2020-01-10T00:00:00Z");
    private static final Instant AFTER_SAME_DAY = Instant.parse("2020-01-11T00:00:00Z");

    private Connection connection;
    private LegalEntityRepository legalEntityRepository;
    private SecurityRepository securityRepository;
    private CorporateActionRepository corporateActionRepository;
    private LineageRepository lineageRepository;
    private LineageResolutionSupport support;

    @BeforeEach
    void setUp() throws Exception {
        Peer.createDatabase(DB_URI);
        connection = Peer.connect(DB_URI);
        connection.transact(DatomicSchema.allSchema()).get();
        connection.transact(DatomicSchema.allEnums()).get();

        legalEntityRepository = new LegalEntityRepository(connection);
        securityRepository = new SecurityRepository(connection);
        corporateActionRepository = new CorporateActionRepository(connection);
        lineageRepository = new LineageRepository(connection);
        support = new LineageResolutionSupport(
                connection,
                securityRepository,
                legalEntityRepository,
                lineageRepository,
                corporateActionRepository,
                new DtoMapper()
        );

        seedBaseScenario();
    }

    @AfterEach
    void tearDown() {
        Peer.deleteDatabase(DB_URI);
    }

    @Test
    void resolveSecurityAncestorsOrdersSameDayEntriesByParentSecurityId() {
        List<SecurityLineageEntryResponse> lineage = support.resolveSecurityAncestors(CHILD_ID, AFTER_SAME_DAY);

        assertThat(lineage)
                .extracting(SecurityLineageEntryResponse::parentId)
                .containsExactly(PARENT_SMALL_ID, PARENT_LARGE_ID);
    }

    @Test
    void buildSecurityNameHistoryOrdersSameDayEntriesBySecurityId() throws Exception {
        applySecurityRename(
                LARGE_PARENT_RENAME_ACTION_ID,
                PARENT_LARGE_ID,
                "Zulu Parent Renamed",
                "US0000000002"
        );
        applySecurityRename(
                SMALL_PARENT_RENAME_ACTION_ID,
                PARENT_SMALL_ID,
                "Alpha Parent Renamed",
                "US0000000001"
        );

        List<SecurityLineageEntryResponse> lineage = support.resolveSecurityAncestors(CHILD_ID, AFTER_SAME_DAY);

        assertThat(support.buildSecurityNameHistory(CHILD_ID, lineage, null, List.of(), AFTER_SAME_DAY).security())
                .extracting(entry -> entry.newName())
                .containsExactly("Alpha Parent Renamed", "Zulu Parent Renamed");
    }

    private void seedBaseScenario() throws Exception {
        Object issuerRef = Peer.tempid(":db.part/user");
        connection.transact(Util.list(
                legalEntityRepository.buildCreateTxMap(
                        ISSUER_ID,
                        "Issuer Corp",
                        LegalEntityType.COMPANY,
                        Date.from(Instant.parse("2010-01-01T00:00:00Z")),
                        issuerRef
                ),
                securityRepository.buildCreateTxMap(
                        PARENT_SMALL_ID,
                        "Alpha Parent",
                        SecurityType.EQUITY,
                        issuerRef,
                        Date.from(ISSUE_DATE),
                        "US1111111111",
                        null,
                        Peer.tempid(":db.part/user")
                ),
                securityRepository.buildCreateTxMap(
                        PARENT_LARGE_ID,
                        "Zulu Parent",
                        SecurityType.EQUITY,
                        issuerRef,
                        Date.from(ISSUE_DATE),
                        "US2222222222",
                        null,
                        Peer.tempid(":db.part/user")
                ),
                securityRepository.buildCreateTxMap(
                        CHILD_ID,
                        "Combined Child",
                        SecurityType.EQUITY,
                        issuerRef,
                        Date.from(ISSUE_DATE),
                        "US3333333333",
                        null,
                        Peer.tempid(":db.part/user")
                )
        )).get();

        Object firstActionRef = Peer.tempid(":db.part/user");
        Object secondActionRef = Peer.tempid(":db.part/user");

        connection.transact(Util.list(
                corporateActionRepository.buildCreateTxMap(
                        LARGE_PARENT_LINEAGE_ACTION_ID,
                        CorporateActionType.MERGER,
                        Date.from(SAME_DAY),
                        "Zulu parent merges into child",
                        null,
                        firstActionRef
                ),
                lineageRepository.buildSecurityLineageTxMap(
                        Util.list(":security/id", PARENT_LARGE_ID),
                        Util.list(":security/id", CHILD_ID),
                        firstActionRef,
                        Peer.tempid(":db.part/user"),
                        UUID.randomUUID()
                )
        )).get();

        connection.transact(Util.list(
                corporateActionRepository.buildCreateTxMap(
                        SMALL_PARENT_LINEAGE_ACTION_ID,
                        CorporateActionType.MERGER,
                        Date.from(SAME_DAY),
                        "Alpha parent merges into child",
                        null,
                        secondActionRef
                ),
                lineageRepository.buildSecurityLineageTxMap(
                        Util.list(":security/id", PARENT_SMALL_ID),
                        Util.list(":security/id", CHILD_ID),
                        secondActionRef,
                        Peer.tempid(":db.part/user"),
                        UUID.randomUUID()
                )
        )).get();
    }

    private void applySecurityRename(UUID actionId, UUID securityId, String newName, String newIsin) throws Exception {
        connection.transact(Util.list(
                corporateActionRepository.buildCreateTxMap(
                        actionId,
                        CorporateActionType.NAME_CHANGE,
                        Date.from(SAME_DAY),
                        "Rename " + securityId,
                        null,
                        Peer.tempid(":db.part/user")
                ),
                securityRepository.buildRenameTxMap(securityId, newName, newIsin)
        )).get();
    }
}
