package com.ufis.simulator;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Component
public class DataSimulator {
    private static final LocalDate DEFAULT_START_DATE = LocalDate.of(2000, 1, 1);

    private static final List<String> NAME_PREFIXES = List.of(
            "Alpha", "Atlas", "Blue", "Cedar", "Delta", "Evergreen", "Frontier", "Golden", "Harbor", "Ion",
            "Juniper", "Keystone", "Liberty", "Monarch", "North", "Oak", "Pioneer", "Quantum", "Ridge", "Summit",
            "Titan", "Union", "Vertex", "West", "Zenith"
    );

    private static final List<String> NAME_SUFFIXES = List.of(
            "Capital", "Group", "Holdings", "Ventures", "Partners", "Markets", "Investments", "Technologies",
            "Logistics", "Resources", "Industries", "Infrastructure", "Systems", "Energy", "Dynamics"
    );

    private static final List<String> REBRAND_SUFFIXES = List.of(
            "Technologies", "Holdings", "Platforms", "Dynamics", "Capital", "Ventures", "Systems"
    );

    public GeneratedDataset generate(Tier tier) {
        return generate(new Config(42L, DEFAULT_START_DATE), tier);
    }

    public GeneratedDataset generate(Config config, Tier tier) {
        Random random = new Random(config.seed() + (long) tier.ordinal() * 10_000L);

        Map<UUID, MutableEntity> entities = new LinkedHashMap<>();
        Map<UUID, MutableSecurity> securities = new LinkedHashMap<>();
        List<SimulatedCorporateAction> actions = new ArrayList<>();
        Map<CorporateActionType, Integer> actionCounts = new EnumMap<>(CorporateActionType.class);
        Map<UUID, Set<LocalDate>> securityDates = new HashMap<>();
        EdgeCaseTracker edgeCases = new EdgeCaseTracker();

        List<SimulatedLegalEntity> initialEntities = createInitialEntities(config, tier, random, entities);
        List<SimulatedSecurity> initialSecurities = createInitialSecurities(config, tier, random, entities, securities);

        seedRequiredScenarios(config, random, entities, securities, actions, actionCounts, securityDates, edgeCases);

        while (actions.size() < tier.corporateActions()) {
            CorporateActionType type = chooseActionType(random);
            if (!applyAction(type, config, tier, random, entities, securities, actions, actionCounts, securityDates, edgeCases)
                    && !applyFallbackAction(config, tier, random, entities, securities, actions, actionCounts, securityDates, edgeCases)) {
                break;
            }
        }

        List<SimulatedLegalEntity> allEntities = entities.values().stream()
                .sorted(Comparator.comparing(MutableEntity::id))
                .map(MutableEntity::snapshot)
                .toList();
        List<SimulatedSecurity> allSecurities = securities.values().stream()
                .sorted(Comparator.comparing(MutableSecurity::id))
                .map(MutableSecurity::snapshot)
                .toList();

        return new GeneratedDataset(
                tier,
                config,
                initialEntities,
                initialSecurities,
                allEntities,
                allSecurities,
                List.copyOf(actions),
                buildActionCounts(actionCounts),
                edgeCases.snapshot()
        );
    }

    private List<SimulatedLegalEntity> createInitialEntities(Config config, Tier tier, Random random, Map<UUID, MutableEntity> entities) {
        List<SimulatedLegalEntity> initialEntities = new ArrayList<>();

        for (int index = 0; index < tier.legalEntities(); index++) {
            LegalEntityType type = chooseEntityType(random);
            LocalDate foundedDate = config.startDate().minusYears(20L + random.nextInt(20)).plusDays(random.nextInt(365));
            MutableEntity entity = new MutableEntity(
                    nextUuid(random),
                    nextEntityName(index, random),
                    type,
                    foundedDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            );
            entities.put(entity.id(), entity);
            initialEntities.add(entity.snapshot());
        }

        return List.copyOf(initialEntities);
    }

    private List<SimulatedSecurity> createInitialSecurities(Config config, Tier tier, Random random, Map<UUID, MutableEntity> entities, Map<UUID, MutableSecurity> securities) {
        int equities = Math.round(tier.securities() * 0.75f);
        int bonds = tier.securities() - equities;
        List<MutableEntity> issuers = new ArrayList<>(entities.values());
        List<SimulatedSecurity> initialSecurities = new ArrayList<>();

        for (int index = 0; index < tier.securities(); index++) {
            SecurityType type = index < equities ? SecurityType.EQUITY : SecurityType.BOND;
            MutableEntity issuer = issuers.get(index % issuers.size());
            LocalDate issueDate = config.startDate().plusDays(random.nextInt(Math.max(365, tier.years() * 365 / 2)));
            Instant maturityDate = type == SecurityType.BOND
                    ? issueDate.plusYears(5 + random.nextInt(15)).atStartOfDay().toInstant(ZoneOffset.UTC)
                    : null;
            MutableSecurity security = new MutableSecurity(
                    nextUuid(random),
                    issuer.name + " " + (type == SecurityType.EQUITY ? "Equity" : "Bond " + (2025 + random.nextInt(10))),
                    type,
                    issuer.id,
                    issueDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                    nextIsin(index + 1),
                    maturityDate
            );
            securities.put(security.id(), security);
            issuer.securityIds.add(security.id());
            initialSecurities.add(security.snapshot());
        }

        if (bonds == 0 && !securities.isEmpty()) {
            MutableSecurity first = securities.values().iterator().next();
            first.type = SecurityType.BOND;
            first.maturityDate = first.issueDate.plusSeconds(86400L * 365 * 10);
            // Resnapshot the overridden security so initial secs reflects the bond type
            for (int i = 0; i < initialSecurities.size(); i++) {
                if (initialSecurities.get(i).id().equals(first.id())) {
                    initialSecurities.set(i, first.snapshot());
                    break;
                }
            }
        }

        return List.copyOf(initialSecurities);
    }

    private void seedRequiredScenarios(Config config, Random random, Map<UUID, MutableEntity> entities,
                                       Map<UUID, MutableSecurity> securities, List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts,
                                       Map<UUID, Set<LocalDate>> securityDates, EdgeCaseTracker edgeCases) {
        List<MutableEntity> entityList = new ArrayList<>(entities.values());

        if (entityList.size() < 4) {
            return;
        }

        MutableEntity entityA = entityList.get(0);
        MutableEntity entityB = entityList.get(1);
        MutableEntity entityC = entityList.get(2);
        MutableEntity entityD = entityList.get(3);

        MutableSecurity survivingSecurity = findSecurityByIssuer(securities, entityA.id, SecurityType.BOND);

        if (survivingSecurity == null) {
            survivingSecurity = findSecurityByIssuer(securities, entityA.id, null);
            if (survivingSecurity != null) {
                survivingSecurity.type = SecurityType.BOND;
                survivingSecurity.maturityDate = survivingSecurity.issueDate.plusSeconds(86400L * 365 * 10);
            }
        }

        MutableSecurity splitSource = findActiveSecurity(
                securities,
                security -> security.issuerId.equals(entityC.id) && security.type == SecurityType.EQUITY
        );
        MutableSecurity spinParent = findActiveSecurity(securities, security -> security.issuerId.equals(entityD.id));

        if (survivingSecurity == null || splitSource == null || spinParent == null) {
            return;
        }

        applyNameChange(
                config.startDate().plusYears(1).plusDays(10),
                random,
                entityA,
                List.of(),
                actions,
                actionCounts,
                securityDates
        );

        applyMerger(
                config.startDate().plusYears(2).plusDays(20),
                random,
                entityA,
                entityB,
                false,
                entities,
                securities,
                actions,
                actionCounts,
                securityDates
        );

        edgeCases.survivingSecurityAfterIssuerMerger = true;
        edgeCases.entityWithMultipleActions = true;

        MutableSecurity splitResult = applyStockSplit(
                config.startDate().plusYears(3).plusDays(30),
                random,
                splitSource,
                securities,
                actions,
                actionCounts,
                securityDates
        );

        applyRedemption(
                config.startDate().plusYears(4).plusDays(40),
                splitResult,
                random,
                actions,
                actionCounts,
                securityDates
        );

        edgeCases.redeemedSecurityMidChain = true;

        applySpinOff(
                config.startDate().plusYears(5).plusDays(50),
                random,
                entityD,
                spinParent,
                entities,
                securities,
                actions,
                actionCounts,
                securityDates
        );

        edgeCases.spinOffCreatesEntityAndSecurity = true;
    }

    private boolean applyAction(CorporateActionType type, Config config, Tier tier,
                                Random random, Map<UUID, MutableEntity> entities, Map<UUID, MutableSecurity> securities,
                                List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates, EdgeCaseTracker edgeCases) {
        LocalDate validDate = nextRandomDate(config, tier, random);

        return switch (type) {
            case NAME_CHANGE -> tryRandomNameChange(validDate, random, entities, securities, actions, actionCounts, securityDates);
            case MERGER -> tryRandomMerger(validDate, random, entities, securities, actions, actionCounts, securityDates);
            case ACQUISITION -> tryRandomAcquisition(validDate, random, entities, securities, actions, actionCounts, securityDates);
            case SPIN_OFF -> tryRandomSpinOff(validDate, random, entities, securities, actions, actionCounts, securityDates, edgeCases);
            case STOCK_SPLIT -> tryRandomStockSplit(validDate, random, securities, actions, actionCounts, securityDates);
            case REDEMPTION -> tryRandomRedemption(validDate, random, securities, actions, actionCounts, securityDates);
        };
    }

    private boolean applyFallbackAction(Config config, Tier tier, Random random,
                                        Map<UUID, MutableEntity> entities, Map<UUID, MutableSecurity> securities, List<SimulatedCorporateAction> actions,
                                        Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates, EdgeCaseTracker edgeCases) {

        for (CorporateActionType candidate : List.of(
                CorporateActionType.NAME_CHANGE,
                CorporateActionType.SPIN_OFF,
                CorporateActionType.STOCK_SPLIT,
                CorporateActionType.REDEMPTION,
                CorporateActionType.ACQUISITION,
                CorporateActionType.MERGER
        )) {

            if (applyAction(candidate, config, tier, random, entities, securities, actions, actionCounts, securityDates, edgeCases)) {
                return true;
            }

        }

        return false;
    }

    private boolean tryRandomNameChange(LocalDate validDate, Random random, Map<UUID, MutableEntity> entities,
                                        Map<UUID, MutableSecurity> securities, List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts,
                                        Map<UUID, Set<LocalDate>> securityDates) {
        MutableEntity entity = findActiveEntity(entities);
        if (entity == null) {
            return false;
        }

        List<MutableSecurity> issued = securities.values().stream()
                .filter(security -> security.active && Objects.equals(security.issuerId, entity.id))
                .limit(2)
                .toList();
        applyNameChange(validDate, random, entity, issued, actions, actionCounts, securityDates);
        return true;
    }

    private boolean tryRandomMerger(LocalDate validDate, Random random, Map<UUID, MutableEntity> entities,
                                    Map<UUID, MutableSecurity> securities, List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts,
                                    Map<UUID, Set<LocalDate>> securityDates) {
        List<MutableEntity> activeEntities = entities.values().stream()
                .filter(MutableEntity::active)
                .limit(4)
                .toList();
        if (activeEntities.size() < 2) {
            return false;
        }
        applyMerger(
                validDate,
                random,
                activeEntities.get(0),
                activeEntities.get(1),
                random.nextBoolean(),
                entities,
                securities,
                actions,
                actionCounts,
                securityDates
        );
        return true;
    }

    private boolean tryRandomAcquisition(LocalDate validDate, Random random, Map<UUID, MutableEntity> entities,
                                         Map<UUID, MutableSecurity> securities, List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates) {
        List<MutableEntity> activeEntities = entities.values().stream()
                .filter(MutableEntity::active)
                .limit(4)
                .toList();
        if (activeEntities.size() < 2) {
            return false;
        }
        MutableEntity acquirer = activeEntities.get(0);
        MutableEntity target = activeEntities.get(1);

        List<UUID> updatedSecurities = new ArrayList<>();
        for (MutableSecurity security : securities.values()) {
            if (security.active && Objects.equals(security.issuerId, target.id)) {
                updatedSecurities.add(security.id);
            }
        }
        LocalDate effectiveDate = nextAvailableDate(validDate, updatedSecurities, securityDates);
        reserveSecurityDate(effectiveDate, updatedSecurities, securityDates);

        for (MutableSecurity security : securities.values()) {
            if (security.active && Objects.equals(security.issuerId, target.id)) {
                security.issuerId = acquirer.id;
            }
        }
        target.active = false;
        target.state = LegalEntityState.ACQUIRED;

        actions.add(new SimulatedCorporateAction(
                nextUuid(random),
                CorporateActionType.ACQUISITION,
                toInstant(effectiveDate),
                acquirer.name + " acquires " + target.name,
                null,
                List.of(acquirer.id, target.id),
                List.of(),
                List.of(),
                List.of(),
                acquirer.id,
                target.id,
                null,
                List.copyOf(updatedSecurities),
                List.of(),
                List.of(),
                null,
                List.of()
        ));
        increment(actionCounts, CorporateActionType.ACQUISITION);
        return true;
    }

    private boolean tryRandomSpinOff(LocalDate validDate, Random random, Map<UUID, MutableEntity> entities,
                                     Map<UUID, MutableSecurity> securities, List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts,
                                     Map<UUID, Set<LocalDate>> securityDates, EdgeCaseTracker edgeCases) {
        MutableEntity parent = findActiveEntity(entities);
        MutableSecurity parentSecurity = findActiveSecurity(securities, security -> parent != null && Objects.equals(security.issuerId, parent.id));
        if (parent == null || parentSecurity == null) {
            return false;
        }
        applySpinOff(validDate, random, parent, parentSecurity, entities, securities, actions, actionCounts, securityDates);
        edgeCases.spinOffCreatesEntityAndSecurity = true;
        return true;
    }

    private boolean tryRandomStockSplit(LocalDate validDate, Random random, Map<UUID, MutableSecurity> securities,
                                        List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates) {
        MutableSecurity source = findActiveSecurity(securities, security -> security.type == SecurityType.EQUITY);
        if (source == null) {
            return false;
        }
        applyStockSplit(validDate, random, source, securities, actions, actionCounts, securityDates);
        return true;
    }

    private boolean tryRandomRedemption(LocalDate validDate, Random random, Map<UUID, MutableSecurity> securities,
                                        List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates) {
        MutableSecurity security = findActiveSecurity(securities, candidate -> candidate.type == SecurityType.BOND);
        if (security == null) {
            security = findActiveSecurity(securities, candidate -> true);
        }
        if (security == null) {
            return false;
        }
        applyRedemption(validDate, security, random, actions, actionCounts, securityDates);
        return true;
    }

    private void applyNameChange(LocalDate validDate, Random random, MutableEntity entity,
                                 List<MutableSecurity> issuedSecurities, List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates) {
        String previousName = entity.name;
        entity.name = baseLabel(previousName) + " " + REBRAND_SUFFIXES.get(random.nextInt(REBRAND_SUFFIXES.size()));
        List<UUID> renamedSecurityIds = new ArrayList<>();
        List<SimulatedSecurityRename> securityRenames = new ArrayList<>();
        for (MutableSecurity security : issuedSecurities) {
            if (reserveSecurityDate(validDate, List.of(security.id), securityDates)) {
                security.name = entity.name + " " + (security.type == SecurityType.EQUITY ? "Equity" : "Bond");
                security.isin = nextIsin(Math.abs(security.id.hashCode()));
                renamedSecurityIds.add(security.id);
                securityRenames.add(new SimulatedSecurityRename(security.id, security.name, security.isin));
            }
        }

        actions.add(new SimulatedCorporateAction(
                nextUuid(random),
                CorporateActionType.NAME_CHANGE,
                toInstant(validDate),
                previousName + " rebrands to " + entity.name,
                null,
                List.of(entity.id),
                List.copyOf(renamedSecurityIds),
                List.of(),
                List.of(),
                null,
                null,
                entity.id,
                List.of(),
                List.of(),
                List.of(),
                entity.name,
                List.copyOf(securityRenames)
        ));
        increment(actionCounts, CorporateActionType.NAME_CHANGE);
    }

    private void applyMerger(LocalDate validDate, Random random, MutableEntity sourceA, MutableEntity sourceB,
                             boolean createResultSecurity, Map<UUID, MutableEntity> entities, Map<UUID, MutableSecurity> securities,
                             List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates) {
        if (!sourceA.active || !sourceB.active) {
            return;
        }

        MutableEntity result = new MutableEntity(
                nextUuid(random),
                baseLabel(sourceA.name) + baseLabel(sourceB.name) + " Group",
                LegalEntityType.COMPANY,
                toInstant(validDate)
        );
        entities.put(result.id, result);
        SimulatedLegalEntity resultSnapshot = result.snapshot();

        List<UUID> updatedSecurities = new ArrayList<>();
        List<UUID> sourceSecurityIds = new ArrayList<>();
        UUID resultSecurityId = null;
        SimulatedSecurity resultSecuritySnapshot = null;

        if (createResultSecurity) {
            List<MutableSecurity> eligible = securities.values().stream()
                    .filter(security -> security.active && (Objects.equals(security.issuerId, sourceA.id) || Objects.equals(security.issuerId, sourceB.id)))
                    .limit(2)
                    .toList();
            if (eligible.size() == 2) {
                MutableSecurity resultSecurity = new MutableSecurity(
                        nextUuid(random),
                        result.name + " Equity",
                        SecurityType.EQUITY,
                        result.id,
                        toInstant(validDate),
                        nextIsin(Math.abs(result.id.hashCode())),
                        null
                );
                securities.put(resultSecurity.id, resultSecurity);
                result.securityIds.add(resultSecurity.id);
                resultSecurityId = resultSecurity.id;
                resultSecuritySnapshot = resultSecurity.snapshot();
                for (MutableSecurity sourceSecurity : eligible) {
                    sourceSecurity.active = false;
                    sourceSecurity.state = SecurityState.MERGED;
                    sourceSecurityIds.add(sourceSecurity.id);
                }
            }
        }

        for (MutableSecurity security : securities.values()) {
            if (security.active && (Objects.equals(security.issuerId, sourceA.id) || Objects.equals(security.issuerId, sourceB.id))) {
                updatedSecurities.add(security.id);
            }
        }
        LocalDate effectiveDate = nextAvailableDate(validDate, combineIds(sourceSecurityIds, updatedSecurities), securityDates);
        reserveSecurityDate(effectiveDate, combineIds(sourceSecurityIds, updatedSecurities), securityDates);

        for (MutableSecurity security : securities.values()) {
            if (security.active && (Objects.equals(security.issuerId, sourceA.id) || Objects.equals(security.issuerId, sourceB.id))) {
                security.issuerId = result.id;
            }
        }

        sourceA.active = false;
        sourceA.state = LegalEntityState.MERGED;
        sourceB.active = false;
        sourceB.state = LegalEntityState.MERGED;

        actions.add(new SimulatedCorporateAction(
                nextUuid(random),
                CorporateActionType.MERGER,
                toInstant(effectiveDate),
                sourceA.name + " merges with " + sourceB.name,
                null,
                List.of(sourceA.id, sourceB.id),
                List.copyOf(sourceSecurityIds),
                List.of(result.id),
                resultSecurityId == null ? List.of() : List.of(resultSecurityId),
                null,
                null,
                null,
                List.copyOf(updatedSecurities),
                List.of(resultSnapshot),
                resultSecuritySnapshot == null ? List.of() : List.of(resultSecuritySnapshot),
                null,
                List.of()
        ));
        increment(actionCounts, CorporateActionType.MERGER);
    }

    private void applySpinOff(LocalDate validDate, Random random, MutableEntity parent,
                              MutableSecurity parentSecurity, Map<UUID, MutableEntity> entities, Map<UUID, MutableSecurity> securities,
                              List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates) {
        LocalDate effectiveDate = nextAvailableDate(validDate, List.of(parentSecurity.id), securityDates);
        reserveSecurityDate(effectiveDate, List.of(parentSecurity.id), securityDates);

        MutableEntity result = new MutableEntity(
                nextUuid(random),
                baseLabel(parent.name) + " Ventures",
                LegalEntityType.COMPANY,
                toInstant(effectiveDate)
        );
        entities.put(result.id, result);
        SimulatedLegalEntity resultSnapshot = result.snapshot();

        MutableSecurity resultSecurity = new MutableSecurity(
                nextUuid(random),
                result.name + " Equity",
                SecurityType.EQUITY,
                result.id,
                toInstant(effectiveDate),
                nextIsin(Math.abs(result.id.hashCode())),
                null
        );
        securities.put(resultSecurity.id, resultSecurity);
        result.securityIds.add(resultSecurity.id);
        SimulatedSecurity resultSecuritySnapshot = resultSecurity.snapshot();

        actions.add(new SimulatedCorporateAction(
                nextUuid(random),
                CorporateActionType.SPIN_OFF,
                toInstant(effectiveDate),
                parent.name + " spins off " + result.name,
                null,
                List.of(parent.id),
                List.of(parentSecurity.id),
                List.of(result.id),
                List.of(resultSecurity.id),
                null,
                null,
                parent.id,
                List.of(),
                List.of(resultSnapshot),
                List.of(resultSecuritySnapshot),
                null,
                List.of()
        ));
        increment(actionCounts, CorporateActionType.SPIN_OFF);
    }

    private MutableSecurity applyStockSplit(LocalDate validDate, Random random, MutableSecurity source,
                                            Map<UUID, MutableSecurity> securities, List<SimulatedCorporateAction> actions, Map<CorporateActionType, Integer> actionCounts, Map<UUID, Set<LocalDate>> securityDates) {
        LocalDate effectiveDate = nextAvailableDate(validDate, List.of(source.id), securityDates);
        reserveSecurityDate(effectiveDate, List.of(source.id), securityDates);
        source.active = false;
        source.state = SecurityState.SPLIT;

        MutableSecurity result = new MutableSecurity(
                nextUuid(random),
                source.name + " (post-split)",
                source.type,
                source.issuerId,
                toInstant(effectiveDate),
                nextIsin(Math.abs(source.id.hashCode()) + 7),
                source.maturityDate
        );
        securities.put(result.id, result);
        SimulatedSecurity resultSnapshot = result.snapshot();

        actions.add(new SimulatedCorporateAction(
                nextUuid(random),
                CorporateActionType.STOCK_SPLIT,
                toInstant(effectiveDate),
                source.name + " executes a stock split",
                "2:1",
                List.of(),
                List.of(source.id),
                List.of(),
                List.of(result.id),
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(resultSnapshot),
                null,
                List.of()
        ));
        increment(actionCounts, CorporateActionType.STOCK_SPLIT);
        return result;
    }

    private void applyRedemption(LocalDate validDate,
                                 MutableSecurity security,
                                 Random random,
                                 List<SimulatedCorporateAction> actions,
                                 Map<CorporateActionType, Integer> actionCounts,
                                 Map<UUID, Set<LocalDate>> securityDates) {
        LocalDate effectiveDate = nextAvailableDate(validDate, List.of(security.id), securityDates);
        reserveSecurityDate(effectiveDate, List.of(security.id), securityDates);
        security.active = false;
        security.state = SecurityState.REDEEMED;
        actions.add(new SimulatedCorporateAction(
                nextUuid(random),
                CorporateActionType.REDEMPTION,
                toInstant(effectiveDate),
                security.name + " is redeemed",
                null,
                List.of(),
                List.of(security.id),
                List.of(),
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of()
        ));
        increment(actionCounts, CorporateActionType.REDEMPTION);
    }

    private boolean reserveSecurityDate(LocalDate validDate, List<UUID> securityIds, Map<UUID, Set<LocalDate>> securityDates) {
        for (UUID securityId : securityIds) {
            if (securityDates.computeIfAbsent(securityId, ignored -> new LinkedHashSet<>()).contains(validDate)) {
                return false;
            }
        }

        for (UUID securityId : securityIds) {
            securityDates.computeIfAbsent(securityId, ignored -> new LinkedHashSet<>()).add(validDate);
        }

        return true;
    }

    private LocalDate nextAvailableDate(LocalDate candidate, List<UUID> securityIds, Map<UUID, Set<LocalDate>> securityDates) {
        LocalDate current = candidate;

        while (hasSecurityDateConflict(current, securityIds, securityDates)) {
            current = current.plusDays(1);
        }

        return current;
    }

    private boolean hasSecurityDateConflict(LocalDate date, List<UUID> securityIds, Map<UUID, Set<LocalDate>> securityDates) {

        for (UUID securityId : securityIds) {
            if (securityDates.getOrDefault(securityId, Set.of()).contains(date)) {
                return true;
            }
        }

        return false;
    }

    private List<UUID> combineIds(List<UUID> first, List<UUID> second) {
        LinkedHashSet<UUID> combined = new LinkedHashSet<>(first);
        combined.addAll(second);

        return List.copyOf(combined);
    }

    private MutableEntity findActiveEntity(Map<UUID, MutableEntity> entities) {
        return entities.values().stream().filter(MutableEntity::active).findFirst().orElse(null);
    }

    private MutableSecurity findSecurityByIssuer(Map<UUID, MutableSecurity> securities, UUID issuerId, SecurityType type) {
        return findActiveSecurity(securities, security -> Objects.equals(security.issuerId, issuerId) && (type == null || security.type == type));
    }

    private MutableSecurity findActiveSecurity(Map<UUID, MutableSecurity> securities,
                                               java.util.function.Predicate<MutableSecurity> predicate) {
        return securities.values().stream()
                .filter(security -> security.active)
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    private LocalDate nextRandomDate(Config config, Tier tier, Random random) {
        return config.startDate().plusDays(random.nextInt(Math.max(365, tier.years() * 365)));
    }

    private CorporateActionType chooseActionType(Random random) {
        int roll = random.nextInt(100);
        if (roll < 30) {
            return CorporateActionType.NAME_CHANGE;
        }
        if (roll < 55) {
            return CorporateActionType.MERGER;
        }
        if (roll < 70) {
            return CorporateActionType.ACQUISITION;
        }
        if (roll < 85) {
            return CorporateActionType.SPIN_OFF;
        }
        if (roll < 95) {
            return CorporateActionType.STOCK_SPLIT;
        }
        return CorporateActionType.REDEMPTION;
    }

    private LegalEntityType chooseEntityType(Random random) {
        int roll = random.nextInt(100);
        if (roll < 80) {
            return LegalEntityType.COMPANY;
        }
        if (roll < 90) {
            return LegalEntityType.FUND;
        }
        if (roll < 95) {
            return LegalEntityType.SPV;
        }
        return LegalEntityType.GOVERNMENT;
    }

    private String nextEntityName(int index, Random random) {
        return NAME_PREFIXES.get(index % NAME_PREFIXES.size()) + " " + NAME_SUFFIXES.get(random.nextInt(NAME_SUFFIXES.size()));
    }

    private String baseLabel(String name) {
        String token = name.split(" ")[0];
        return token.length() <= 12 ? token : token.substring(0, 12);
    }

    private String nextIsin(int index) {
        return "US%010d".formatted(Math.abs(index % 1_000_000_000));
    }

    private UUID nextUuid(Random random) {
        return new UUID(random.nextLong(), random.nextLong());
    }

    private Instant toInstant(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private void increment(Map<CorporateActionType, Integer> actionCounts, CorporateActionType type) {
        actionCounts.merge(type, 1, Integer::sum);
    }

    private Map<CorporateActionType, Integer> buildActionCounts(Map<CorporateActionType, Integer> actionCounts) {
        Map<CorporateActionType, Integer> counts = new EnumMap<>(CorporateActionType.class);
        for (CorporateActionType type : CorporateActionType.values()) {
            counts.put(type, actionCounts.getOrDefault(type, 0));
        }
        return counts;
    }

    public enum Tier {
        SMALL(10, 30, 50, 10),
        MEDIUM(100, 500, 2_000, 20),
        LARGE(1_000, 5_000, 20_000, 30);

        private final int legalEntities;
        private final int securities;
        private final int corporateActions;
        private final int years;

        Tier(int legalEntities, int securities, int corporateActions, int years) {
            this.legalEntities = legalEntities;
            this.securities = securities;
            this.corporateActions = corporateActions;
            this.years = years;
        }

        public int legalEntities() {
            return legalEntities;
        }

        public int securities() {
            return securities;
        }

        public int corporateActions() {
            return corporateActions;
        }

        public int years() {
            return years;
        }
    }

    public record Config(long seed, LocalDate startDate) {}

    public record GeneratedDataset(
            Tier tier,
            Config config,
            List<SimulatedLegalEntity> initialEntities,
            List<SimulatedSecurity> initialSecurities,
            List<SimulatedLegalEntity> allEntities,
            List<SimulatedSecurity> allSecurities,
            List<SimulatedCorporateAction> corporateActions,
            Map<CorporateActionType, Integer> actionCounts,
            EdgeCaseCoverage edgeCases
    ) {
    }

    public record SimulatedLegalEntity(
            UUID id,
            String name,
            LegalEntityType type,
            LegalEntityState state,
            boolean active,
            Instant foundedDate
    ) {
    }

    public record SimulatedSecurity(
            UUID id,
            String name,
            String isin,
            SecurityType type,
            SecurityState state,
            boolean active,
            UUID issuerId,
            Instant issueDate,
            Instant maturityDate
    ) {
    }

    public record SimulatedCorporateAction(
            UUID id,
            CorporateActionType type,
            Instant validDate,
            String description,
            String splitRatio,
            List<UUID> entityIds,
            List<UUID> securityIds,
            List<UUID> createdEntityIds,
            List<UUID> createdSecurityIds,
            UUID acquirerEntityId,
            UUID targetEntityId,
            UUID subjectEntityId,
            List<UUID> issuerUpdatedSecurityIds,
            List<SimulatedLegalEntity> createdEntitiesSnapshot,
            List<SimulatedSecurity> createdSecuritiesSnapshot,
            String newEntityName,
            List<SimulatedSecurityRename> securityRenames
    ) {
    }

    public record SimulatedSecurityRename(UUID securityId, String newName, String newIsin) {}

    public record EdgeCaseCoverage(boolean survivingSecurityAfterIssuerMerger, boolean redeemedSecurityMidChain, boolean entityWithMultipleActions, boolean spinOffCreatesEntityAndSecurity) {}

    private static final class EdgeCaseTracker {
        private boolean survivingSecurityAfterIssuerMerger;
        private boolean redeemedSecurityMidChain;
        private boolean entityWithMultipleActions;
        private boolean spinOffCreatesEntityAndSecurity;

        private EdgeCaseCoverage snapshot() {
            return new EdgeCaseCoverage(survivingSecurityAfterIssuerMerger, redeemedSecurityMidChain, entityWithMultipleActions, spinOffCreatesEntityAndSecurity);
        }
    }

    private static final class MutableEntity {
        private final UUID id;
        private String name;
        private final LegalEntityType type;
        private LegalEntityState state = LegalEntityState.ACTIVE;
        private boolean active = true;
        private final Instant foundedDate;
        private final Set<UUID> securityIds = new LinkedHashSet<>();

        private MutableEntity(UUID id, String name, LegalEntityType type, Instant foundedDate) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.foundedDate = foundedDate;
        }

        private UUID id() {
            return id;
        }

        private boolean active() {
            return active;
        }

        private SimulatedLegalEntity snapshot() {
            return new SimulatedLegalEntity(id, name, type, state, active, foundedDate);
        }
    }

    private static final class MutableSecurity {
        private final UUID id;
        private String name;
        private String isin;
        private SecurityType type;
        private SecurityState state = SecurityState.ACTIVE;
        private boolean active = true;
        private UUID issuerId;
        private final Instant issueDate;
        private Instant maturityDate;

        private MutableSecurity(UUID id, String name, SecurityType type, UUID issuerId, Instant issueDate, String isin, Instant maturityDate) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.issuerId = issuerId;
            this.issueDate = issueDate;
            this.isin = isin;
            this.maturityDate = maturityDate;
        }

        private UUID id() {
            return id;
        }

        private SimulatedSecurity snapshot() {
            return new SimulatedSecurity(id, name, isin, type, state, active, issuerId, issueDate, maturityDate);
        }
    }
}
