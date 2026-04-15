# UFIS Developer Guide

## What UFIS Is

UFIS (Unified Financial Instrument Identifier System) is a dissertation project by Charles Trolle (University of Stirling, supervised by Dr. Ashley McClenaghan, industry partner Level E Research). It solves a specific problem in financial data: standard identifiers like ISIN and CUSIP treat securities as static, but corporate actions (mergers, spin-offs, redemptions, etc.) continuously reshape a security's identity. UFIS builds an **immutable, bitemporal lineage graph** so that given any `(security_id, timestamp)` pair, you can resolve the full ancestry and state of that security at that exact moment.

The backend is Spring Boot + Datomic (in-memory by default). The frontend is a React investigation UI for exploring lineage, auditing bitemporal divergence, and browsing the dataset.

---

## Architecture at a Glance

```
webapp/ (React + Vite + TanStack Router)
   |
   | HTTP (port 5173 → proxy to 8080)
   v
Spring Boot API (port 8080)
   |
   v
Datomic Peer (in-memory)
```

**Backend layout:** `src/main/java/com/ufis/` — `controller/`, `dto/`, `repository/`, `service/`, `service/lineage/`, `simulator/`, `validation/`

**Frontend layout:** `webapp/src/` — `api/` (client + schemas), `app/` (shell, router, breadcrumbs), `components/`, `features/` (audit diff engine, lineage graph builders), `routes/`, `styles/`

---

## Two Lineage Graphs

UFIS tracks **legal entities** (companies/issuers) and **securities** (bonds, equities) as separate lineage chains. A security references an entity as its issuer, but they evolve independently. A legal entity merger does not automatically terminate its securities — it changes who is responsible for them.

**Corporate action types:** MERGER, ACQUISITION, SPIN_OFF, NAME_CHANGE, STOCK_SPLIT, REDEMPTION.

**Bitemporal model:**
- **Valid time** (`valid-date`) — when the action occurred in the real world. Used by all standard queries.
- **Transaction time** — when the fact was recorded in Datomic. Used by audit queries via `db.asOf()`.

This means you can ask: "What did we know on recording-date X about events that happened on real-world-date Y?"

---

## Running the App

```bash
# Start the backend (port 8080)
./gradlew bootRun

# Start the frontend dev server (port 5173) — needs Node.js on PATH
cd webapp && npm run dev
```

Open `http://localhost:5173`. Click **Seed Dataset** on the home page to populate the database with synthetic data.

---

## Frontend Routes and What They Do

| Route | Page | Purpose |
|---|---|---|
| `/` | HomePage | Dashboard with seed controls and recent corporate action feed |
| `/search?q=...` | SearchPage | Global search across securities and legal entities |
| `/browse` | BrowsePage | Filter securities/entities by type (EQUITY, BOND) and state (ACTIVE, MERGED, etc.) |
| `/security/:id` | SecurityPage | Security workspace: summary card, lineage graph, timeline feed, name history. Accepts `?validAt=` for point-in-time view |
| `/security/:id/audit` | SecurityAuditPage | Bitemporal audit comparison. Accepts `?validAt=&knownAt=`. Has divergence scanner, root cause analysis, field diffs, and report export |
| `/legal-entity/:id` | LegalEntityPage | Entity workspace: summary, lineage graph, timeline. Accepts `?validAt=` |
| `/corporate-action/:id` | CorporateActionPage | Action detail: before/after entity visualization, affected securities, lineage records |

---

## Key Frontend Features

### Lineage Graphs
Built in `features/security-lineage/graph.ts` and `features/entity-lineage/graph.ts`. Transform API lineage responses into node/edge structures rendered by `LineageGraph` component. Nodes are color-coded: cyan for active, amber for historical, red for terminal.

### Audit Comparison
The audit page compares standard lineage (latest DB state + valid-time filter) against audit lineage (`db.asOf(knownAt)` + valid-time filter). The diff engine in `features/audit/diff.ts` produces:
- **Field-level differences** with explanations
- **Node comparison** showing which nodes diverge and why
- **Unknown actions** — actions in the standard view that weren't yet recorded at audit time (the root cause of divergence)
- **English summary** of the analysis

The "Scan for Divergence" button iterates over audit hint timestamps (captured during seeding) to automatically find securities with bitemporal differences.

### Seeding and Audit Hints
`DummyDataSeedService` inserts corporate actions in 3 staggered batches with 1.5s gaps. The batch boundary timestamps are returned as `auditHintTimestamps` and stored in `localStorage`. These become clickable presets on the audit page, making it easy to demonstrate bitemporal divergence without manual timestamp entry.

### Design System
Dark theme built for financial investigation. Tokens in `webapp/src/styles/tokens.css`, component styles in `webapp/src/styles/base.css`. Fonts: DM Serif Text (headings), DM Sans (body), JetBrains Mono (data). Sharp edges, no glassmorphism.

---

## API Endpoints Summary

**Core CRUD:** `POST/GET /legal-entity`, `POST/GET /security`, `GET /corporate-action/{id}`

**Lineage:** `GET /security/{id}/lineage?validAt=`, `GET /legal-entity/{id}/lineage?validAt=`, `GET /security/{id}/lineage/audit?validAt=&knownAt=`

**Action lists:** `GET /security/{id}/actions`, `GET /legal-entity/{id}/actions`

**Corporate actions:** `POST /corporate-action/{name-change,redemption,stock-split,acquisition,spin-off,merger}`

**Browse/feed:** `GET /security?type=&state=`, `GET /legal-entity?type=&state=`, `GET /corporate-action/recent?limit=`, `GET /corporate-action/{id}/detail`

**Seeding:** `POST /dummy-data/seed?size={SMALL,MEDIUM,LARGE}`

---
