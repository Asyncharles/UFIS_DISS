package com.ufis.validation;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.DatomicEnumMapper;
import com.ufis.repository.DatomicSchema;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import datomic.Connection;
import datomic.Peer;
import datomic.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class CorporateActionValidatorTest {

    private static final String DB_URI = "datomic:mem://ufis-validator-test";

    private Connection connection;
    private LegalEntityRepository legalEntityRepository;
    private SecurityRepository securityRepository;
    private CorporateActionRepository corporateActionRepository;
    private LineageRepository lineageRepository;
    private CorporateActionValidator validator;

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
        validator = new CorporateActionValidator(
                connection,
                legalEntityRepository,
                securityRepository,
                corporateActionRepository,
                lineageRepository
        );
    }

    @AfterEach
    void tearDown() {
        Peer.deleteDatabase(DB_URI);
    }

    @Test
    void requireLegalEntityPassesForExistingEntity() throws Exception {
        UUID entityId = legalEntityRepository.create("Alpha Corp", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));

        Map<String, Object> result = validator.requireLegalEntity(entityId);

        assertThat(result.get("id")).isEqualTo(entityId);
    }

    @Test
    void requireLegalEntityFailsForMissingEntity() {
        assertStatus(
                () -> validator.requireLegalEntity(UUID.randomUUID()),
                NOT_FOUND
        );
    }

    @Test
    void requireActiveSecurityPassesForActiveSecurity() throws Exception {
        UUID issuerId = legalEntityRepository.create("Issuer", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID securityId = securityRepository.create("Alpha Equity", SecurityType.EQUITY, issuerId, date("2015-01-01T00:00:00Z"), null, null);

        Map<String, Object> result = validator.requireActiveSecurity(securityId);

        assertThat(result.get("id")).isEqualTo(securityId);
    }

    @Test
    void requireActiveSecurityFailsForTerminalSecurity() throws Exception {
        UUID issuerId = legalEntityRepository.create("Issuer", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID securityId = securityRepository.create("Alpha Equity", SecurityType.EQUITY, issuerId, date("2015-01-01T00:00:00Z"), null, null);

        connection.transact(Util.list(Util.map(
                ":db/id", Util.list(":security/id", securityId),
                ":security/state", DatomicEnumMapper.toKeyword(SecurityState.MERGED),
                ":security/active", false
        ))).get();

        assertStatus(
                () -> validator.requireActiveSecurity(securityId),
                CONFLICT
        );
    }

    @Test
    void validateSecurityStateConsistencyPassesForActivePair() {
        assertThatCode(() -> validator.validateSecurityStateConsistency(SecurityState.ACTIVE, true))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSecurityStateConsistencyFailsForInconsistentPair() {
        assertStatus(
                () -> validator.validateSecurityStateConsistency(SecurityState.REDEEMED, true),
                BAD_REQUEST
        );
    }

    @Test
    void validateLegalEntityStateConsistencyPassesForTerminalPair() {
        assertThatCode(() -> validator.validateLegalEntityStateConsistency(LegalEntityState.MERGED, false))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLegalEntityStateConsistencyFailsForInconsistentPair() {
        assertStatus(
                () -> validator.validateLegalEntityStateConsistency(LegalEntityState.ACTIVE, false),
                BAD_REQUEST
        );
    }

    @Test
    void validateSecurityActionTypePassesForStockSplit() {
        assertThatCode(() -> validator.validateSecurityActionType(CorporateActionType.STOCK_SPLIT))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSecurityActionTypeFailsForAcquisition() {
        assertStatus(
                () -> validator.validateSecurityActionType(CorporateActionType.ACQUISITION),
                BAD_REQUEST
        );
    }

    @Test
    void validateLegalEntityActionTypePassesForAcquisition() {
        assertThatCode(() -> validator.validateLegalEntityActionType(CorporateActionType.ACQUISITION))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLegalEntityActionTypeFailsForRedemption() {
        assertStatus(
                () -> validator.validateLegalEntityActionType(CorporateActionType.REDEMPTION),
                BAD_REQUEST
        );
    }

    @Test
    void ensureSecurityResultIsNewPassesForUnusedId() {
        assertThatCode(() -> validator.ensureSecurityResultIsNew(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureSecurityResultIsNewFailsForExistingSecurity() throws Exception {
        UUID issuerId = legalEntityRepository.create("Issuer", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID securityId = securityRepository.create("Alpha Equity", SecurityType.EQUITY, issuerId, date("2015-01-01T00:00:00Z"), null, null);

        assertStatus(
                () -> validator.ensureSecurityResultIsNew(securityId),
                BAD_REQUEST
        );
    }

    @Test
    void ensureLegalEntityResultIsNewPassesForUnusedId() {
        assertThatCode(() -> validator.ensureLegalEntityResultIsNew(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureLegalEntityResultIsNewFailsForExistingEntity() throws Exception {
        UUID entityId = legalEntityRepository.create("Alpha Corp", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));

        assertStatus(
                () -> validator.ensureLegalEntityResultIsNew(entityId),
                BAD_REQUEST
        );
    }

    @Test
    void ensureNoDuplicateSecurityValidDatePassesWhenDateIsUnused() throws Exception {
        UUID issuerId = legalEntityRepository.create("Issuer", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID source = securityRepository.create("Source", SecurityType.EQUITY, issuerId, date("2015-01-01T00:00:00Z"), null, null);
        UUID child = securityRepository.create("Child", SecurityType.EQUITY, issuerId, date("2016-01-01T00:00:00Z"), null, null);
        UUID actionId = corporateActionRepository.create(CorporateActionType.MERGER, date("2020-06-01T00:00:00Z"), "merge", null);
        lineageRepository.createSecurityLineage(source, child, actionId);

        assertThatCode(() -> validator.ensureNoDuplicateSecurityValidDate(source, Instant.parse("2020-06-02T00:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureNoDuplicateSecurityValidDateFailsWhenDateAlreadyExists() throws Exception {
        UUID issuerId = legalEntityRepository.create("Issuer", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID source = securityRepository.create("Source", SecurityType.EQUITY, issuerId, date("2015-01-01T00:00:00Z"), null, null);
        UUID child = securityRepository.create("Child", SecurityType.EQUITY, issuerId, date("2016-01-01T00:00:00Z"), null, null);
        UUID actionId = corporateActionRepository.create(CorporateActionType.MERGER, date("2020-06-01T00:00:00Z"), "merge", null);
        lineageRepository.createSecurityLineage(source, child, actionId);

        assertStatus(
                () -> validator.ensureNoDuplicateSecurityValidDate(source, Instant.parse("2020-06-01T00:00:00Z")),
                CONFLICT
        );
    }

    @Test
    void ensureNoCircularSecurityLineagePassesForFreshChild() throws Exception {
        UUID issuerId = legalEntityRepository.create("Issuer", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID parent = securityRepository.create("Parent", SecurityType.EQUITY, issuerId, date("2015-01-01T00:00:00Z"), null, null);
        UUID child = securityRepository.create("Child", SecurityType.EQUITY, issuerId, date("2016-01-01T00:00:00Z"), null, null);

        assertThatCode(() -> validator.ensureNoCircularSecurityLineage(parent, child))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureNoCircularSecurityLineageFailsWhenChildIsAlreadyAncestor() throws Exception {
        UUID issuerId = legalEntityRepository.create("Issuer", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID grandparent = securityRepository.create("Grandparent", SecurityType.EQUITY, issuerId, date("2014-01-01T00:00:00Z"), null, null);
        UUID parent = securityRepository.create("Parent", SecurityType.EQUITY, issuerId, date("2015-01-01T00:00:00Z"), null, null);
        UUID child = securityRepository.create("Child", SecurityType.EQUITY, issuerId, date("2016-01-01T00:00:00Z"), null, null);
        UUID actionA = corporateActionRepository.create(CorporateActionType.MERGER, date("2017-01-01T00:00:00Z"), "a", null);
        UUID actionB = corporateActionRepository.create(CorporateActionType.MERGER, date("2018-01-01T00:00:00Z"), "b", null);
        lineageRepository.createSecurityLineage(grandparent, parent, actionA);
        lineageRepository.createSecurityLineage(parent, child, actionB);

        assertStatus(
                () -> validator.ensureNoCircularSecurityLineage(child, grandparent),
                CONFLICT
        );
    }

    @Test
    void ensureNoCircularLegalEntityLineagePassesForFreshChild() throws Exception {
        UUID parent = legalEntityRepository.create("Parent", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID child = legalEntityRepository.create("Child", LegalEntityType.COMPANY, date("2012-01-01T00:00:00Z"));

        assertThatCode(() -> validator.ensureNoCircularLegalEntityLineage(parent, child))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureNoCircularLegalEntityLineageFailsWhenChildIsAlreadyAncestor() throws Exception {
        UUID grandparent = legalEntityRepository.create("Grandparent", LegalEntityType.COMPANY, date("2010-01-01T00:00:00Z"));
        UUID parent = legalEntityRepository.create("Parent", LegalEntityType.COMPANY, date("2011-01-01T00:00:00Z"));
        UUID child = legalEntityRepository.create("Child", LegalEntityType.COMPANY, date("2012-01-01T00:00:00Z"));
        UUID actionA = corporateActionRepository.create(CorporateActionType.MERGER, date("2017-01-01T00:00:00Z"), "a", null);
        UUID actionB = corporateActionRepository.create(CorporateActionType.MERGER, date("2018-01-01T00:00:00Z"), "b", null);
        lineageRepository.createLegalEntityLineage(grandparent, parent, actionA);
        lineageRepository.createLegalEntityLineage(parent, child, actionB);

        assertStatus(
                () -> validator.ensureNoCircularLegalEntityLineage(child, grandparent),
                CONFLICT
        );
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }

    private static void assertStatus(ThrowingRunnable runnable, org.springframework.http.HttpStatus status) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(status);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
