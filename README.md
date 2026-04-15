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
