import { useState, useCallback } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";

import { api } from "../api/client";
import { JsonDrawer } from "../components/JsonDrawer";
import { LineageGraph } from "../components/LineageGraph";
import { SummaryCard } from "../components/SummaryCard";
import { buildSecurityGraph } from "../features/security-lineage/graph";
import { buildAuditAnalysis, AuditAnalysis } from "../features/audit/diff";
import { buildAuditReport } from "../features/audit/export";
import { loadAuditHints } from "../lib/audit-hints";
import { formatDateOnly, formatIdentifier, formatTimestamp, fromDateTimeLocalValue, toDateTimeLocalValue } from "../lib/format";

type SecurityAuditPageProps = {
  id: string;
  validAt?: string;
  knownAt?: string;
};

type ScanResult = {
  knownAt: string;
  diffCount: number;
  analysis: AuditAnalysis | null;
};

export function SecurityAuditPage({ id, validAt, knownAt }: SecurityAuditPageProps) {
  const navigate = useNavigate();
  const [defaultTimestamp] = useState(() => new Date().toISOString());
  const effectiveValidAt = validAt ?? defaultTimestamp;
  const effectiveKnownAt = knownAt ?? defaultTimestamp;

  const [validAtDraft, setValidAtDraft] = useState(toDateTimeLocalValue(effectiveValidAt));
  const [knownAtDraft, setKnownAtDraft] = useState(toDateTimeLocalValue(effectiveKnownAt));
  const [reportCopied, setReportCopied] = useState(false);
  const [scanResults, setScanResults] = useState<ScanResult[] | null>(null);
  const [scanning, setScanning] = useState(false);

  const auditHints = loadAuditHints();

  // Fetch the security's actions to get meaningful validAt presets
  const actionsQuery = useQuery({
    queryKey: ["security-actions", id],
    queryFn: () => api.getSecurityActions(id),
  });

  const standardQuery = useQuery({
    queryKey: ["security-lineage", id, effectiveValidAt],
    queryFn: () => api.getSecurityLineage(id, effectiveValidAt),
  });
  const auditQuery = useQuery({
    queryKey: ["security-audit-lineage", id, effectiveValidAt, effectiveKnownAt],
    queryFn: () => api.getSecurityAuditLineage(id, effectiveValidAt, effectiveKnownAt),
  });

  // Build validAt presets from the security's corporate action dates
  const validAtPresets: string[] = [];
  if (actionsQuery.data) {
    const seen = new Set<string>();
    for (const entry of actionsQuery.data) {
      const date = entry.action.validDate;
      if (!seen.has(date)) {
        seen.add(date);
        validAtPresets.push(date);
      }
    }
  }

  const handleScan = useCallback(async () => {
    if (auditHints.length === 0) return;
    setScanning(true);
    const results: ScanResult[] = [];

    for (const hint of auditHints) {
      try {
        const [standard, audit] = await Promise.all([
          api.getSecurityLineage(id, effectiveValidAt),
          api.getSecurityAuditLineage(id, effectiveValidAt, hint),
        ]);
        const analysis = buildAuditAnalysis(standard, audit);
        const nodeChanges = [...analysis.nodeComparison.securityNodes, ...analysis.nodeComparison.issuerNodes]
          .filter((n) => n.status !== "matched").length;
        results.push({
          knownAt: hint,
          diffCount: analysis.differences.length + nodeChanges + analysis.unknownActions.length,
          analysis,
        });
      } catch {
        results.push({
          knownAt: hint,
          diffCount: -1,
          analysis: null,
        });
      }
    }

    setScanResults(results);
    setScanning(false);
  }, [id, effectiveValidAt, auditHints]);

  function applyPreset(vAt: string, kAt: string) {
    navigate({
      to: "/security/$id/audit",
      params: { id },
      search: { validAt: vAt, knownAt: kAt },
    });
  }

  function handleCopyReport() {
    if (!standardQuery.data || !auditQuery.data) return;
    const analysis = buildAuditAnalysis(standardQuery.data, auditQuery.data);
    const report = buildAuditReport(
      standardQuery.data.security.name,
      effectiveValidAt,
      effectiveKnownAt,
      standardQuery.data,
      auditQuery.data,
      analysis,
    );
    navigator.clipboard.writeText(report).then(() => {
      setReportCopied(true);
      setTimeout(() => setReportCopied(false), 2000);
    });
  }

  // Presets panel (always visible)
  const presetsPanel = (
    <section className="panel panel-padding stack">
      <div className="panel-kicker">Audit Controls</div>

      {validAtPresets.length > 0 ? (
        <div className="preset-group">
          <div className="preset-label">Event dates (validAt)</div>
          <div className="preset-chips">
            {validAtPresets.map((date) => (
              <button
                className={`preset-chip ${effectiveValidAt === date ? "selected" : ""}`}
                key={date}
                onClick={() => {
                  setValidAtDraft(toDateTimeLocalValue(date));
                  applyPreset(date, effectiveKnownAt);
                }}
                type="button"
              >
                {formatDateOnly(date)}
              </button>
            ))}
          </div>
        </div>
      ) : null}

      {auditHints.length > 0 ? (
        <div className="preset-group">
          <div className="preset-label">
            System knowledge cutoffs (knownAt) — what the system knew at each point
          </div>
          <div className="preset-chips">
            {auditHints.map((ts, i) => (
              <button
                className={`preset-chip ${effectiveKnownAt === ts ? "selected" : ""}`}
                key={ts}
                onClick={() => {
                  setKnownAtDraft(toDateTimeLocalValue(ts));
                  applyPreset(effectiveValidAt, ts);
                }}
                type="button"
              >
                After batch {i + 1} — partial knowledge
              </button>
            ))}
            <button
              className={`preset-chip ${!auditHints.includes(effectiveKnownAt) ? "selected" : ""}`}
              onClick={() => {
                const now = new Date().toISOString();
                setKnownAtDraft(toDateTimeLocalValue(now));
                applyPreset(effectiveValidAt, now);
              }}
              type="button"
            >
              Now — full knowledge
            </button>
          </div>
        </div>
      ) : (
        <div className="empty-state" style={{ padding: 16, fontSize: "0.8rem" }}>
          No audit timestamps available. Seed data to generate bitemporal test scenarios.
        </div>
      )}

      <div className="preset-group">
        <div className="preset-label">Manual override</div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <label style={{ display: "grid", gap: 4, flex: 1, minWidth: 180 }}>
            <span className="text-muted mono" style={{ fontSize: "0.65rem" }}>VALID AT</span>
            <input
              className="time-input"
              onChange={(e) => setValidAtDraft(e.target.value)}
              type="datetime-local"
              value={validAtDraft}
            />
          </label>
          <label style={{ display: "grid", gap: 4, flex: 1, minWidth: 180 }}>
            <span className="text-muted mono" style={{ fontSize: "0.65rem" }}>KNOWN AT</span>
            <input
              className="time-input"
              onChange={(e) => setKnownAtDraft(e.target.value)}
              type="datetime-local"
              value={knownAtDraft}
            />
          </label>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button
            className="btn-primary"
            onClick={() => applyPreset(
              fromDateTimeLocalValue(validAtDraft) ?? effectiveValidAt,
              fromDateTimeLocalValue(knownAtDraft) ?? effectiveKnownAt,
            )}
            type="button"
          >
            Apply
          </button>
          <button className="btn-ghost" onClick={handleCopyReport} type="button">
            {reportCopied ? "Copied" : "Copy Report"}
          </button>
        </div>
      </div>

      {auditHints.length > 0 ? (
        <div className="preset-group">
          <button
            className="btn-secondary"
            disabled={scanning}
            onClick={handleScan}
            style={{ width: "100%" }}
            type="button"
          >
            {scanning ? "Scanning..." : "Scan for Divergence"}
          </button>
        </div>
      ) : null}
    </section>
  );

  // Scan results panel
  const scanPanel = scanResults ? (
    <section className="panel panel-padding">
      <div className="panel-header">
        <div>
          <div className="panel-kicker">Scan Results</div>
          <h2 className="panel-title">Divergence at each knowledge point</h2>
        </div>
      </div>
      <div className="scan-results">
        {scanResults.map((result, i) => (
          <button
            className="scan-row"
            key={result.knownAt}
            onClick={() => applyPreset(effectiveValidAt, result.knownAt)}
            type="button"
          >
            <div>
              <div style={{ fontWeight: 600, fontSize: "0.85rem" }}>Batch {i + 1} boundary</div>
              <div className="scan-timestamp">{formatTimestamp(result.knownAt)}</div>
            </div>
            {result.diffCount < 0 ? (
              <span className="scan-badge has-diff">Error</span>
            ) : result.diffCount > 0 ? (
              <span className="scan-badge has-diff">{result.diffCount} differences</span>
            ) : (
              <span className="scan-badge no-diff">No divergence</span>
            )}
            <span className="text-muted" style={{ fontSize: "0.75rem" }}>Click to view</span>
          </button>
        ))}
      </div>
    </section>
  ) : null;

  // Loading / error states
  if (standardQuery.isLoading || auditQuery.isLoading) {
    return (
      <div className="page-grid">
        {presetsPanel}
        <div className="loading-text">Resolving audit comparison...</div>
      </div>
    );
  }

  if (standardQuery.error || auditQuery.error || !standardQuery.data || !auditQuery.data) {
    const message =
      standardQuery.error instanceof Error
        ? standardQuery.error.message
        : auditQuery.error instanceof Error
          ? auditQuery.error.message
          : "Failed to load audit workspace.";
    return (
      <div className="page-grid">
        {presetsPanel}
        <div className="error-panel">
          <div className="error-message">{message}</div>
          <div className="error-hint">Check the security ID and ensure the backend is running.</div>
        </div>
      </div>
    );
  }

  const analysis = buildAuditAnalysis(standardQuery.data, auditQuery.data);
  const standardGraph = buildSecurityGraph(standardQuery.data);
  const auditGraph = buildSecurityGraph(auditQuery.data);
  const allNodes = [...analysis.nodeComparison.securityNodes, ...analysis.nodeComparison.issuerNodes];
  const divergentNodes = allNodes.filter((n) => n.status !== "matched");
  const hasDivergence = analysis.differences.length > 0 || divergentNodes.length > 0 || analysis.unknownActions.length > 0;

  return (
    <div className="page-grid">
      {presetsPanel}
      {scanPanel}

      {hasDivergence ? (
        <div className="audit-summary">{analysis.summary}</div>
      ) : null}

      <div className="audit-columns">
        <SummaryCard
          accent="active"
          eyebrow="Standard Resolution"
          rows={[
            { label: "Security", value: standardQuery.data.security.name },
            { label: "State", value: standardQuery.data.security.state },
            { label: "Issuer", value: standardQuery.data.currentIssuer?.name ?? "None" },
            { label: "Lineage depth", value: `${standardQuery.data.securityLineage.length} security, ${standardQuery.data.issuerLineage.length} issuer` },
            { label: "Resolved at", value: formatTimestamp(standardQuery.data.resolvedAt) },
          ]}
          title={`Valid at ${formatDateOnly(effectiveValidAt)}`}
        />
        <SummaryCard
          accent="historical"
          eyebrow="Audit Resolution"
          rows={[
            { label: "Security", value: auditQuery.data.security.name },
            { label: "State", value: auditQuery.data.security.state },
            { label: "Issuer", value: auditQuery.data.currentIssuer?.name ?? "None" },
            { label: "Lineage depth", value: `${auditQuery.data.securityLineage.length} security, ${auditQuery.data.issuerLineage.length} issuer` },
            { label: "Resolved at", value: formatTimestamp(auditQuery.data.resolvedAt) },
          ]}
          title={`Known at ${formatDateOnly(effectiveKnownAt)}`}
        />
      </div>

      <div className="audit-columns">
        <section className="panel panel-padding">
          <div className="panel-kicker">Standard Lineage</div>
          <LineageGraph edges={standardGraph.edges} nodes={standardGraph.nodes} />
        </section>
        <section className="panel panel-padding">
          <div className="panel-kicker">Audit Lineage</div>
          <LineageGraph edges={auditGraph.edges} nodes={auditGraph.nodes} />
        </section>
      </div>

      {analysis.unknownActions.length > 0 ? (
        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Root Cause</div>
              <h2 className="panel-title">Actions not yet recorded at audit point</h2>
            </div>
          </div>
          <div className="diff-list">
            {analysis.unknownActions.map((action) => (
              <div className="unknown-action-row" key={action.actionId}>
                <span className="pill pill-terminal">{action.actionType.replace("_", " ")}</span>
                <span className="mono text-muted" style={{ fontSize: "0.8rem" }}>{formatDateOnly(action.actionDate)}</span>
                <span style={{ fontSize: "0.85rem" }}>
                  Affected <strong>{action.parentName}</strong> ({action.category})
                </span>
                <span className="mono text-muted" style={{ fontSize: "0.7rem" }}>{formatIdentifier(action.actionId)}</span>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <section className="panel panel-padding">
        <div className="panel-header">
          <div>
            <div className="panel-kicker">Field Differences</div>
            <h2 className="panel-title">Standard vs. audit resolution</h2>
          </div>
        </div>
        {analysis.differences.length === 0 ? (
          <div className="no-diff">
            No field-level divergence detected.
          </div>
        ) : (
          <div className="stack" style={{ gap: 12 }}>
            {analysis.differences.map((d) => (
              <div className="diff-card" key={d.label}>
                <div className="diff-card-label">{d.label}</div>
                <div className="diff-card-values">
                  <div className="diff-card-val standard">{d.standardValue}</div>
                  <div className="diff-card-arrow">\u2192</div>
                  <div className="diff-card-val audit">{d.auditValue}</div>
                </div>
                <div className="diff-card-explanation">{d.explanation}</div>
              </div>
            ))}
          </div>
        )}
      </section>

      {allNodes.length > 0 ? (
        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Node Comparison</div>
              <h2 className="panel-title">Lineage nodes ({divergentNodes.length} divergent of {allNodes.length})</h2>
            </div>
          </div>
          <div className="diff-list">
            {allNodes.map((node) => (
              <div key={node.id}>
                <div className="node-detail-row">
                  <div>
                    <div style={{ fontWeight: 600, fontSize: "0.85rem" }}>{node.name}</div>
                  </div>
                  <span className="pill" style={{ fontSize: "0.65rem" }}>{node.category}</span>
                  <span className="pill">{node.actionType} \u00b7 {node.actionDate}</span>
                  <span
                    className={`pill ${
                      node.status === "matched"
                        ? ""
                        : node.status === "standard-only"
                          ? "pill-terminal"
                          : "pill-active"
                    }`}
                  >
                    {node.status === "matched"
                      ? "Matched"
                      : node.status === "standard-only"
                        ? "Standard only"
                        : "Audit only"}
                  </span>
                </div>
                {node.status !== "matched" ? (
                  <div className="node-explanation">{node.explanation}</div>
                ) : null}
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <div className="audit-columns">
        <JsonDrawer label="Standard lineage payload" value={standardQuery.data} />
        <JsonDrawer label="Audit lineage payload" value={auditQuery.data} />
      </div>
    </div>
  );
}
