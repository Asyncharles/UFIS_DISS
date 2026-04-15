# UFIS

Unified Financial Instrument Identifier System.

UFIS is a Spring Boot backend for tracking how securities and issuers change over time through corporate actions. It models security lineage and legal-entity lineage separately, stores history in Datomic, and resolves point-in-time identity through the lineage graph instead of treating identifiers as static.

## What It Does

- Creates and reads legal entities, securities, and corporate actions
- Records mergers, acquisitions, spin-offs, name changes, stock splits, and redemptions
- Resolves security lineage at a given `validAt` timestamp
- Resolves audit lineage using both `validAt` and `knownAt`
- Lists corporate actions affecting a security or issuer
- Generates deterministic synthetic datasets for regression and benchmark work

## Stack

| Layer | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4 |
| Database | Datomic Peer |
| Build | Gradle |
| Tests | JUnit 5 + Spring MVC integration tests |

Default configuration uses an in-memory Datomic database:

```yaml
datomic:
  uri: "datomic:mem://ufis"
```

## Run

Start the API:

```bash
./gradlew bootRun
```

The server listens on `http://localhost:8080`.

Run the test suite:
The following two tests will fail by default as I have added a forced divergence for testing purposes:
- AuditLineageIntegrationTest > auditLineageUsesKnownAtAsTransactionTimeCutoff()
- SearchIntegrationTest > searchReturnsGroupedResultsAcrossCoreObjectTypes()

```bash
./gradlew test
```

Run the lineage benchmark harness:

```bash
./gradlew lineageBenchmark
```

If you only want the simulator or benchmark entrypoints, the main classes are:

- `com.ufis.simulator.SimulatorHarness`
- `com.ufis.benchmark.LineageBenchmarkHarness`

## API Surface

### Core records

- `POST /legal-entity`
- `GET /legal-entity/{id}`
- `POST /security`
- `GET /security/{id}`
- `GET /corporate-action/{id}`

### Lineage

- `GET /legal-entity/{id}/lineage?validAt=...`
- `GET /security/{id}/lineage?validAt=...`
- `GET /security/{id}/lineage/audit?validAt=...&knownAt=...`

### Action lists

- `GET /legal-entity/{id}/actions`
- `GET /security/{id}/actions`

### Corporate actions

- `POST /corporate-action/name-change`
- `POST /corporate-action/redemption`
- `POST /corporate-action/stock-split`
- `POST /corporate-action/acquisition`
- `POST /corporate-action/spin-off`
- `POST /corporate-action/merger`

## Project Layout

```text
src/main/java/com/ufis
├─ controller     REST endpoints
├─ dto            request/response contracts
├─ repository     Datomic schema and queries
├─ service        application services and corporate action handlers
├─ service/lineage lineage resolution and name-history logic
├─ simulator      deterministic dataset generation
└─ validation     lifecycle and graph integrity rules
```

## Current Focus

This repository is backend-only. The current implementation emphasizes:

- deterministic lineage resolution
- state-machine enforcement for corporate actions
- regression coverage for spec scenarios
- benchmarkable synthetic datasets

## AI Usage

AI was used in two ways for this dissertation project:

### Claude opusplan prompts
 
- Based on the technical requirements page and given latex template, generate a plan.md document on how to properly structure an undergraduate CS dissertation report
- How to do mint listings for java
- How can i remove floating images/charts in latex
- Give me a brief feedback summary and honest opinion on this dissertation report for a 4th CS student at the university of stirling. Include references double-check

### Claude opus-4.6

- Based on the available springboot code and dissertation outline, generate a spec.md for an agent to be able to understand this project
- Based on the spec.md, generate a web UI for testing and demo purpose | personal comment: I did this to avoid using postman/insomnia during the demo
- Add more details based on the payload for the audit, ask questions if you have any