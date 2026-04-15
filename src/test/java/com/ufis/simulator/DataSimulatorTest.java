package com.ufis.simulator;

import com.ufis.domain.enums.CorporateActionType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DataSimulatorTest {

    private final DataSimulator simulator = new DataSimulator();

    @Test
    void generateIsDeterministicForSameSeed() {
        DataSimulator.Config config = new DataSimulator.Config(42L, java.time.LocalDate.of(2000, 1, 1));

        DataSimulator.GeneratedDataset first = simulator.generate(config, DataSimulator.Tier.SMALL);
        DataSimulator.GeneratedDataset second = simulator.generate(config, DataSimulator.Tier.SMALL);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void generateProducesConfiguredCountsAndRequiredEdgeCasesForAllTiers() {
        for (DataSimulator.Tier tier : DataSimulator.Tier.values()) {
            DataSimulator.GeneratedDataset dataset = simulator.generate(tier);

            assertThat(dataset.initialEntities()).hasSize(tier.legalEntities());
            assertThat(dataset.initialSecurities()).hasSize(tier.securities());
            assertThat(dataset.corporateActions()).hasSize(tier.corporateActions());
            assertThat(dataset.edgeCases().survivingSecurityAfterIssuerMerger()).isTrue();
            assertThat(dataset.edgeCases().redeemedSecurityMidChain()).isTrue();
            assertThat(dataset.edgeCases().entityWithMultipleActions()).isTrue();
            assertThat(dataset.edgeCases().spinOffCreatesEntityAndSecurity()).isTrue();
        }
    }

    @Test
    void generateAvoidsDuplicateValidDatesPerAffectedSecurity() {
        DataSimulator.GeneratedDataset dataset = simulator.generate(DataSimulator.Tier.MEDIUM);
        Map<UUID, Set<java.time.Instant>> datesBySecurity = new HashMap<>();

        for (DataSimulator.SimulatedCorporateAction action : dataset.corporateActions()) {
            Set<UUID> affectedSecurityIds = new HashSet<>();
            affectedSecurityIds.addAll(action.securityIds());
            affectedSecurityIds.addAll(action.issuerUpdatedSecurityIds());

            for (UUID securityId : affectedSecurityIds) {
                Set<java.time.Instant> dates = datesBySecurity.computeIfAbsent(securityId, ignored -> new HashSet<>());
                assertThat(dates.add(action.validDate()))
                        .as("Duplicate validDate for security %s on action %s", securityId, action.id())
                        .isTrue();
            }
        }
    }

    @Test
    void generateProducesWeightedActionMixWithNameChangesAsMostCommon() {
        DataSimulator.GeneratedDataset dataset = simulator.generate(DataSimulator.Tier.MEDIUM);

        assertThat(dataset.actionCounts().get(CorporateActionType.NAME_CHANGE))
                .isGreaterThan(dataset.actionCounts().get(CorporateActionType.REDEMPTION));
        assertThat(dataset.actionCounts().get(CorporateActionType.NAME_CHANGE))
                .isGreaterThan(dataset.actionCounts().get(CorporateActionType.STOCK_SPLIT));
        assertThat(dataset.actionCounts().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(DataSimulator.Tier.MEDIUM.corporateActions());
    }
}
