package com.ufis.benchmark;

import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.SecurityState;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.DatomicSchema;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.LineageRepository;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.DtoMapper;
import com.ufis.service.lineage.AuditLineageService;
import com.ufis.service.lineage.LegalEntityLineageService;
import com.ufis.service.lineage.LineageResolutionSupport;
import com.ufis.service.lineage.SecurityLineageService;
import com.ufis.simulator.DataSimulator;
import datomic.Connection;
import datomic.Peer;
import datomic.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LineageBenchmarkHarness {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private static final Path REPORT_PATH = Path.of("build", "reports", "benchmarks", "lineage-benchmark-results.md");

    private LineageBenchmarkHarness() {}

    public static void main(String[] args) throws Exception {
        List<DataSimulator.Tier> tiers = parseTiers(args);
        List<TierBenchmarkResult> results = new ArrayList<>();

        for (DataSimulator.Tier tier : tiers) {
            results.add(runTier(tier));
        }

        String report = toMarkdown(results);
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report);
        System.out.println(report);
        System.out.println();
        System.out.println("Benchmark report written to " + REPORT_PATH.toAbsolutePath());
    }

    private static List<DataSimulator.Tier> parseTiers(String[] args) {
        if (args.length == 0) {
            return List.of(DataSimulator.Tier.SMALL, DataSimulator.Tier.MEDIUM, DataSimulator.Tier.LARGE);
        }

        List<DataSimulator.Tier> tiers = new ArrayList<>();

        for (String arg : args) {
            tiers.add(DataSimulator.Tier.valueOf(arg.trim().toUpperCase()));
        }

        return List.copyOf(tiers);
    }

    private static TierBenchmarkResult runTier(DataSimulator.Tier tier) throws Exception {
        String dbUri = "datomic:mem://ufis-benchmark-" + tier.name().toLowerCase() + "-" + UUID.randomUUID();
        Peer.createDatabase(dbUri);

        try {
            Connection connection = Peer.connect(dbUri);
            connection.transact(DatomicSchema.allSchema()).get();
            connection.transact(DatomicSchema.allEnums()).get();

            BenchmarkSettings settings = settingsFor(tier);
            DataSimulator simulator = new DataSimulator();
            DataSimulator.GeneratedDataset dataset = simulator.generate(tier);
            BenchmarkFixture fixture = loadDataset(connection, dataset, settings.sampleSize());

            SecurityRepository securityRepository = new SecurityRepository(connection);
            LegalEntityRepository legalEntityRepository = new LegalEntityRepository(connection);
            LineageRepository lineageRepository = new LineageRepository(connection);
            CorporateActionRepository corporateActionRepository = new CorporateActionRepository(connection);

            LineageResolutionSupport support = new LineageResolutionSupport(connection, securityRepository, legalEntityRepository, lineageRepository, corporateActionRepository, new DtoMapper());

            SecurityLineageService securityLineageService = new SecurityLineageService(support);
            LegalEntityLineageService legalEntityLineageService = new LegalEntityLineageService(support);
            AuditLineageService auditLineageService = new AuditLineageService(connection, support);

            warmUpSecurity(securityLineageService, fixture.securitySampleIds(), fixture.validAt(), settings.warmupRounds());
            warmUpLegalEntity(legalEntityLineageService, fixture.entitySampleIds(), fixture.validAt(), settings.warmupRounds());
            warmUpAudit(auditLineageService, fixture.securitySampleIds(), fixture.validAt(), fixture.knownAt(), settings.warmupRounds());

            LatencyStats securityStats = measure(fixture.securitySampleIds(), settings.measuredRounds(), id -> securityQueryResult(securityLineageService, id, fixture.validAt())),
                    legalEntityStats = measure(fixture.entitySampleIds(), settings.measuredRounds(), id -> legalEntityQueryResult(legalEntityLineageService, id, fixture.validAt())),
                    auditStats = measure(fixture.securitySampleIds(), settings.measuredRounds(), id -> auditQueryResult(auditLineageService, id, fixture.validAt(), fixture.knownAt()));

            SecurityStageProfile securityStageProfile = profileSecurityStages(support, fixture.securitySampleIds(), fixture.validAt());

            return new TierBenchmarkResult(tier, fixture.securitySampleIds().size(), fixture.entitySampleIds().size(), fixture.loadDuration(), securityStats,
                    legalEntityStats, auditStats, securityStageProfile, settings);
        } finally {
            Peer.deleteDatabase(dbUri);
        }
    }

    private static BenchmarkSettings settingsFor(DataSimulator.Tier tier) {
        return switch (tier) {
            case SMALL -> new BenchmarkSettings(10, 1, 3);
            case MEDIUM -> new BenchmarkSettings(10, 1, 3);
            case LARGE -> new BenchmarkSettings(5, 1, 2);
        };
    }

    private static BenchmarkFixture loadDataset(Connection connection, DataSimulator.GeneratedDataset dataset, int sampleSize) throws Exception {
        DatasetLoader loader = new DatasetLoader(connection);
        Instant start = Instant.now();
        loader.seedInitialDataset(dataset);

        for (DataSimulator.SimulatedCorporateAction action : dataset.corporateActions()) {
            loader.replayAction(action);
        }

        Instant end = Instant.now();
        Instant validAt = dataset.corporateActions().stream()
                .map(DataSimulator.SimulatedCorporateAction::validDate)
                .max(Comparator.naturalOrder())
                .orElseThrow()
                .plusSeconds(1);

        return new BenchmarkFixture(pickSecuritySampleIds(dataset, sampleSize), pickEntitySampleIds(dataset, sampleSize), validAt, loader.lastKnownAt(), Duration.between(start, end));
    }

    private static List<UUID> pickSecuritySampleIds(DataSimulator.GeneratedDataset dataset, int sampleSize) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();

        for (DataSimulator.SimulatedCorporateAction action : dataset.corporateActions()) {
            ids.addAll(action.securityIds());
            ids.addAll(action.createdSecurityIds());
            ids.addAll(action.issuerUpdatedSecurityIds());
        }

        for (DataSimulator.SimulatedSecurity security : dataset.allSecurities()) {
            ids.add(security.id());
        }

        return ids.stream().limit(sampleSize).toList();
    }

    private static List<UUID> pickEntitySampleIds(DataSimulator.GeneratedDataset dataset, int sampleSize) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();

        for (DataSimulator.SimulatedCorporateAction action : dataset.corporateActions()) {
            ids.addAll(action.entityIds());
            ids.addAll(action.createdEntityIds());
            if (action.acquirerEntityId() != null) {
                ids.add(action.acquirerEntityId());
            }
            if (action.targetEntityId() != null) {
                ids.add(action.targetEntityId());
            }
            if (action.subjectEntityId() != null) {
                ids.add(action.subjectEntityId());
            }
        }

        for (DataSimulator.SimulatedLegalEntity entity : dataset.allEntities()) {
            ids.add(entity.id());
        }

        return ids.stream().limit(sampleSize).toList();
    }

    private static void warmUpSecurity(SecurityLineageService service, List<UUID> ids, Instant validAt, int warmupRounds) {
        warmUp(ids, warmupRounds, id -> securityQueryResult(service, id, validAt));
    }

    private static void warmUpLegalEntity(LegalEntityLineageService service, List<UUID> ids, Instant validAt, int warmupRounds) {
        warmUp(ids, warmupRounds, id -> legalEntityQueryResult(service, id, validAt));
    }

    private static void warmUpAudit(AuditLineageService service, List<UUID> ids, Instant validAt, Instant knownAt, int warmupRounds) {
        warmUp(ids, warmupRounds, id -> auditQueryResult(service, id, validAt, knownAt));
    }

    private static void warmUp(List<UUID> ids, int warmupRounds, BenchmarkQuery query) {
        for (int round = 0; round < warmupRounds; round++) {
            for (UUID id : ids) {
                query.execute(id);
            }
        }
    }

    private static int securityQueryResult(SecurityLineageService service, UUID id, Instant validAt) {
        var response = service.getLineage(id, validAt);
        return response.securityLineage().size() + response.issuerLineage().size();
    }

    private static int legalEntityQueryResult(LegalEntityLineageService service, UUID id, Instant validAt) {
        return service.getLineage(id, validAt).issuerLineage().size();
    }

    private static int auditQueryResult(AuditLineageService service, UUID id, Instant validAt, Instant knownAt) {
        var response = service.getSecurityAuditLineage(id, validAt, knownAt);
        return response.securityLineage().size() + response.issuerLineage().size();
    }

    private static SecurityStageProfile profileSecurityStages(LineageResolutionSupport support, List<UUID> ids, Instant validAt) {
        long securityResponseNanos = 0L;
        long securityAncestorsNanos = 0L;
        long issuerResolutionNanos = 0L;
        long nameHistoryNanos = 0L;

        for (UUID id : ids) {
            long start = System.nanoTime();
            var security = support.resolveSecurityResponse(id, validAt);
            securityResponseNanos += System.nanoTime() - start;

            start = System.nanoTime();
            var securityLineage = support.resolveSecurityAncestors(id, validAt);
            securityAncestorsNanos += System.nanoTime() - start;

            start = System.nanoTime();
            UUID currentIssuerId = security.issuer() == null ? null : security.issuer().id();
            var issuerLineage = currentIssuerId == null ? List.<com.ufis.dto.response.LegalEntityLineageEntryResponse>of() : support.resolveLegalEntityAncestors(currentIssuerId, validAt);

            if (currentIssuerId != null) {
                support.resolveLegalEntityResponse(currentIssuerId, validAt);
            }

            issuerResolutionNanos += System.nanoTime() - start;

            start = System.nanoTime();
            support.buildSecurityNameHistory(id, securityLineage, currentIssuerId, issuerLineage, validAt);
            nameHistoryNanos += System.nanoTime() - start;
        }

        return new SecurityStageProfile(nanosToMillis(securityResponseNanos / (double) ids.size()), nanosToMillis(securityAncestorsNanos / (double) ids.size()),
                nanosToMillis(issuerResolutionNanos / (double) ids.size()), nanosToMillis(nameHistoryNanos / (double) ids.size()));
    }

    private static LatencyStats measure(List<UUID> ids, int measuredRounds, BenchmarkQuery query) {
        List<Long> samples = new ArrayList<>(ids.size() * measuredRounds);
        long checksum = 0L;

        for (int round = 0; round < measuredRounds; round++) {
            for (UUID id : ids) {
                long start = System.nanoTime();
                checksum += query.execute(id);
                samples.add(System.nanoTime() - start);
            }
        }

        List<Long> sorted = samples.stream().sorted().toList();
        long totalNanos = samples.stream().mapToLong(Long::longValue).sum();
        long p95 = sorted.get(Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1)));
        long max = sorted.getLast();

        return new LatencyStats(samples.size(), nanosToMillis(totalNanos / (double) samples.size()), nanosToMillis(p95), nanosToMillis(max), checksum);
    }

    private static String toMarkdown(List<TierBenchmarkResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Phase 18 Benchmark Results\n\n");
        builder.append("Tier settings:\n");
        builder.append("- Small: sample 10, warm-up 1, measured 3\n");
        builder.append("- Medium: sample 10, warm-up 1, measured 3\n");
        builder.append("- Large: sample 5, warm-up 1, measured 2\n\n");
        builder.append("`validAt` is measured one second after the latest simulator action in each dataset. ");
        builder.append("Audit queries use the final transaction timestamp after dataset replay as `knownAt`.\n\n");
        builder.append("| Tier | Load Time (ms) | Security Avg | Security P95 | Security Max | Legal Avg | Legal P95 | Legal Max | Audit Avg | Audit P95 | Audit Max | Samples |\n");
        builder.append("|------|----------------|--------------|--------------|--------------|-----------|-----------|-----------|-----------|-----------|-----------|---------|\n");

        for (TierBenchmarkResult result : results) {
            builder.append("| ")
                    .append(result.tier().name())
                    .append(" | ")
                    .append(format(result.loadDuration().toMillis()))
                    .append(" | ")
                    .append(format(result.securityStats().averageMillis()))
                    .append(" | ")
                    .append(format(result.securityStats().p95Millis()))
                    .append(" | ")
                    .append(format(result.securityStats().maxMillis()))
                    .append(" | ")
                    .append(format(result.legalEntityStats().averageMillis()))
                    .append(" | ")
                    .append(format(result.legalEntityStats().p95Millis()))
                    .append(" | ")
                    .append(format(result.legalEntityStats().maxMillis()))
                    .append(" | ")
                    .append(format(result.auditStats().averageMillis()))
                    .append(" | ")
                    .append(format(result.auditStats().p95Millis()))
                    .append(" | ")
                    .append(format(result.auditStats().maxMillis()))
                    .append(" | ")
                    .append(result.securitySampleCount())
                    .append("/")
                    .append(result.entitySampleCount())
                    .append(" |\n");
        }

        builder.append('\n');
        for (TierBenchmarkResult result : results) {
            builder.append("## ").append(result.tier().name()).append('\n');
            builder.append("- Settings: sample ")
                    .append(result.settings().sampleSize())
                    .append(", warm-up ")
                    .append(result.settings().warmupRounds())
                    .append(", measured ")
                    .append(result.settings().measuredRounds())
                    .append('\n');
            builder.append("- Security checksum: ").append(result.securityStats().checksum()).append('\n');
            builder.append("- Legal-entity checksum: ").append(result.legalEntityStats().checksum()).append('\n');
            builder.append("- Audit checksum: ").append(result.auditStats().checksum()).append('\n');
            builder.append("- Security stage averages: response ")
                    .append(format(result.securityStageProfile().securityResponseAvgMillis()))
                    .append(" ms, security-lineage ")
                    .append(format(result.securityStageProfile().securityAncestorsAvgMillis()))
                    .append(" ms, issuer-resolution ")
                    .append(format(result.securityStageProfile().issuerResolutionAvgMillis()))
                    .append(" ms, name-history ")
                    .append(format(result.securityStageProfile().nameHistoryAvgMillis()))
                    .append(" ms\n");
            if (result.tier() == DataSimulator.Tier.MEDIUM) {
                boolean passes = result.securityStats().maxMillis() <= 300.0 && result.legalEntityStats().maxMillis() <= 300.0;
                builder.append("- Medium-tier target: ")
                        .append(passes ? "PASS" : "FAIL")
                        .append(" (security max ")
                        .append(format(result.securityStats().maxMillis()))
                        .append(" ms, legal max ")
                        .append(format(result.legalEntityStats().maxMillis()))
                        .append(" ms)\n");
            }
            builder.append('\n');
        }

        builder.append("## Notes\n");
        builder.append("- The stage breakdown isolates where security-lineage time is spent, so benchmark failures can be tied to specific resolver phases instead of only top-line latency.\n");
        builder.append("- Audit queries add `db.asOf` and history-db lookups on top of the same lineage resolver, so they should be evaluated separately from baseline lineage latency.\n");
        return builder.toString();
    }

    private static String format(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1000000.0;
    }

    @FunctionalInterface
    private interface BenchmarkQuery {
        int execute(UUID id);
    }

    private record BenchmarkFixture(List<UUID> securitySampleIds, List<UUID> entitySampleIds, Instant validAt, Instant knownAt, Duration loadDuration) {}

    private record LatencyStats(int queryCount, double averageMillis, double p95Millis, double maxMillis, long checksum) {}

    private record SecurityStageProfile(double securityResponseAvgMillis, double securityAncestorsAvgMillis, double issuerResolutionAvgMillis, double nameHistoryAvgMillis) {}

    private record BenchmarkSettings(int sampleSize, int warmupRounds, int measuredRounds) {}

    private record TierBenchmarkResult(DataSimulator.Tier tier, int securitySampleCount, int entitySampleCount, Duration loadDuration, LatencyStats securityStats, LatencyStats legalEntityStats,
                                       LatencyStats auditStats, SecurityStageProfile securityStageProfile, BenchmarkSettings settings) {}

    private static final class DatasetLoader {
        private final Connection connection;
        private final LegalEntityRepository legalEntityRepository;
        private final SecurityRepository securityRepository;
        private final CorporateActionRepository corporateActionRepository;
        private final LineageRepository lineageRepository;
        private Instant lastKnownAt = Instant.now();

        private DatasetLoader(Connection connection) {
            this.connection = connection;
            this.legalEntityRepository = new LegalEntityRepository(connection);
            this.securityRepository = new SecurityRepository(connection);
            this.corporateActionRepository = new CorporateActionRepository(connection);
            this.lineageRepository = new LineageRepository(connection);
        }

        private void seedInitialDataset(DataSimulator.GeneratedDataset dataset) throws Exception {
            List<Object> tx = new ArrayList<>();
            Map<UUID, Object> entityRefs = new HashMap<>();

            for (DataSimulator.SimulatedLegalEntity entity : dataset.initialEntities()) {
                Object entityRef = Peer.tempid(":db.part/user");
                entityRefs.put(entity.id(), entityRef);
                tx.add(legalEntityRepository.buildCreateTxMap(
                        entity.id(),
                        entity.name(),
                        entity.type(),
                        java.util.Date.from(entity.foundedDate()),
                        entityRef
                ));
            }

            for (DataSimulator.SimulatedSecurity security : dataset.initialSecurities()) {
                tx.add(securityRepository.buildCreateTxMap(
                        security.id(), security.name(), security.type(),
                        entityRefs.getOrDefault(security.issuerId(), Util.list(":legal-entity/id", security.issuerId())),
                        java.util.Date.from(security.issueDate()), security.isin(),
                        security.maturityDate() == null ? null : java.util.Date.from(security.maturityDate()),
                        Peer.tempid(":db.part/user")
                ));
            }

            connection.transact(tx).get();
            lastKnownAt = Instant.now();
        }

        private void replayAction(DataSimulator.SimulatedCorporateAction action) throws Exception {
            List<Object> tx = new ArrayList<>();
            Object actionRef = Peer.tempid(":db.part/user");

            tx.add(corporateActionRepository.buildCreateTxMap(action.id(), action.type(),
                    java.util.Date.from(action.validDate()), action.description(), action.splitRatio(), actionRef));

            Object createdEntityRef = null;
            if (!action.createdEntitiesSnapshot().isEmpty()) {
                DataSimulator.SimulatedLegalEntity createdEntity = action.createdEntitiesSnapshot().getFirst();
                createdEntityRef = Peer.tempid(":db.part/user");

                tx.add(legalEntityRepository.buildCreateTxMap(createdEntity.id(), createdEntity.name(), createdEntity.type(), java.util.Date.from(createdEntity.foundedDate()), createdEntityRef));
            }

            Object createdSecurityRef = null;
            if (!action.createdSecuritiesSnapshot().isEmpty()) {
                DataSimulator.SimulatedSecurity createdSecurity = action.createdSecuritiesSnapshot().getFirst();
                createdSecurityRef = Peer.tempid(":db.part/user");
                Object issuerRef = createdEntityRef != null ? createdEntityRef : Util.list(":legal-entity/id", createdSecurity.issuerId());

                tx.add(securityRepository.buildCreateTxMap(createdSecurity.id(), createdSecurity.name(), createdSecurity.type(),
                        issuerRef, java.util.Date.from(createdSecurity.issueDate()), createdSecurity.isin(),
                        createdSecurity.maturityDate() == null ? null : java.util.Date.from(createdSecurity.maturityDate()), createdSecurityRef));
            }

            switch (action.type()) {
                case NAME_CHANGE -> {
                    tx.add(legalEntityRepository.buildRenameTxMap(action.subjectEntityId(), action.newEntityName()));

                    for (DataSimulator.SimulatedSecurityRename rename : action.securityRenames()) {
                        tx.add(securityRepository.buildRenameTxMap(rename.securityId(), rename.newName(), rename.newIsin()));
                    }
                }
                case ACQUISITION -> {
                    tx.add(legalEntityRepository.buildStateTransitionTxMap(action.targetEntityId(), LegalEntityState.ACQUIRED));
                    tx.add(lineageRepository.buildLegalEntityLineageTxMap(Util.list(":legal-entity/id", action.targetEntityId()),
                            Util.list(":legal-entity/id", action.acquirerEntityId()), actionRef, Peer.tempid(":db.part/user"), UUID.randomUUID()));

                    for (UUID securityId : action.issuerUpdatedSecurityIds()) {
                        tx.add(securityRepository.buildIssuerUpdateTxMap(securityId, action.acquirerEntityId()));
                    }
                }
                case MERGER -> {
                    for (UUID entityId : action.entityIds()) {
                        tx.add(legalEntityRepository.buildStateTransitionTxMap(entityId, LegalEntityState.MERGED));
                        tx.add(lineageRepository.buildLegalEntityLineageTxMap(Util.list(":legal-entity/id", entityId), createdEntityRef, actionRef,
                                Peer.tempid(":db.part/user"), UUID.randomUUID()));
                    }

                    for (UUID securityId : action.securityIds()) {
                        tx.add(securityRepository.buildStateTransitionTxMap(securityId, SecurityState.MERGED));
                        tx.add(lineageRepository.buildSecurityLineageTxMap(
                                Util.list(":security/id", securityId),
                                createdSecurityRef,
                                actionRef,
                                Peer.tempid(":db.part/user"),
                                UUID.randomUUID()
                        ));
                    }

                    for (UUID securityId : action.issuerUpdatedSecurityIds()) {
                        tx.add(securityRepository.buildIssuerUpdateTxMap(securityId, createdEntityRef));
                    }
                }
                case SPIN_OFF -> {
                    tx.add(lineageRepository.buildLegalEntityLineageTxMap(Util.list(":legal-entity/id", action.subjectEntityId()), createdEntityRef,
                            actionRef, Peer.tempid(":db.part/user"), UUID.randomUUID()));

                    tx.add(lineageRepository.buildSecurityLineageTxMap(Util.list(":security/id", action.securityIds().getFirst()), createdSecurityRef,
                            actionRef, Peer.tempid(":db.part/user"), UUID.randomUUID()));
                }
                case STOCK_SPLIT -> {
                    UUID sourceSecurityId = action.securityIds().getFirst();

                    tx.add(securityRepository.buildStateTransitionTxMap(sourceSecurityId, SecurityState.SPLIT));
                    tx.add(lineageRepository.buildSecurityLineageTxMap(Util.list(":security/id", sourceSecurityId), createdSecurityRef,
                            actionRef, Peer.tempid(":db.part/user"), UUID.randomUUID()));
                }
                case REDEMPTION -> {
                    UUID sourceSecurityId = action.securityIds().getFirst();

                    tx.add(securityRepository.buildStateTransitionTxMap(sourceSecurityId, SecurityState.REDEEMED));
                    tx.add(lineageRepository.buildSecurityLineageTxMap(Util.list(":security/id", sourceSecurityId), null,
                            actionRef, Peer.tempid(":db.part/user"), UUID.randomUUID()));
                }
            }

            connection.transact(tx).get();
            lastKnownAt = Instant.now();
        }

        private Instant lastKnownAt() {
            return lastKnownAt;
        }
    }
}