import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";

import { api } from "../api/client";
import { JsonDrawer } from "../components/JsonDrawer";
import { LineageGraph } from "../components/LineageGraph";
import { NameHistoryTable } from "../components/NameHistoryTable";
import { SummaryCard } from "../components/SummaryCard";
import { TimeControls } from "../components/TimeControls";
import { TimelineFeed } from "../components/TimelineFeed";
import { buildSecurityGraph } from "../features/security-lineage/graph";
import { useBreadcrumbLabel } from "../app/BreadcrumbContext";
import { remember } from "../features/search/search-links";
import { formatDateOnly, formatTimestamp } from "../lib/format";

type SecurityPageProps = {
  id: string;
  validAt?: string;
};

export function SecurityPage({ id, validAt }: SecurityPageProps) {
  const navigate = useNavigate();
  const { setLabel } = useBreadcrumbLabel();
  const lineageQuery = useQuery({
    queryKey: ["security-lineage", id, validAt ?? null],
    queryFn: () => api.getSecurityLineage(id, validAt),
  });
  const actionsQuery = useQuery({
    queryKey: ["security-actions", id],
    queryFn: () => api.getSecurityActions(id),
  });

  useEffect(() => {
    if (!lineageQuery.data) return;
    setLabel(`security-${id}`, lineageQuery.data.security.name);
    remember({
      kind: "security",
      id: lineageQuery.data.security.id,
      label: lineageQuery.data.security.name,
      subtitle: lineageQuery.data.security.isin ?? lineageQuery.data.security.type,
      href: `/security/${lineageQuery.data.security.id}`,
    });
  }, [lineageQuery.data]);

  if (lineageQuery.isLoading || actionsQuery.isLoading) {
    return (
      <div className="workspace-grid">
        <aside className="workspace-rail">
          <div className="skeleton" style={{ height: 200 }} />
          <div className="skeleton" style={{ height: 160 }} />
        </aside>
        <main className="workspace-main">
          <div className="skeleton" style={{ height: 520 }} />
          <div className="skeleton" style={{ height: 200 }} />
        </main>
        <aside className="workspace-rail">
          <div className="skeleton" style={{ height: 160 }} />
        </aside>
      </div>
    );
  }

  if (lineageQuery.error || actionsQuery.error || !lineageQuery.data || !actionsQuery.data) {
    const message =
      lineageQuery.error instanceof Error
        ? lineageQuery.error.message
        : actionsQuery.error instanceof Error
          ? actionsQuery.error.message
          : "Failed to load security workspace.";
    return (
      <div className="error-panel">
        <div className="error-message">{message}</div>
        <div className="error-hint">Check the security ID and ensure the backend is running.</div>
        <button
          className="btn-secondary"
          onClick={() => {
            lineageQuery.refetch();
            actionsQuery.refetch();
          }}
          style={{ marginTop: 12 }}
          type="button"
        >
          Retry
        </button>
      </div>
    );
  }

  const graph = buildSecurityGraph(lineageQuery.data);
  const accentTone = lineageQuery.data.security.active ? "active" as const : "terminal" as const;

  return (
    <div className="workspace-grid">
      <aside className="workspace-rail">
        <SummaryCard
          accent={accentTone}
          eyebrow="Security"
          highlight={lineageQuery.data.security.isin ?? "No ISIN"}
          rows={[
            { label: "Type", value: lineageQuery.data.security.type },
            { label: "State", value: lineageQuery.data.security.state },
            { label: "Issued", value: formatDateOnly(lineageQuery.data.security.issueDate) },
            { label: "Maturity", value: formatDateOnly(lineageQuery.data.security.maturityDate) },
          ]}
          title={lineageQuery.data.security.name}
        />
        <SummaryCard
          eyebrow="Issuer"
          rows={[
            {
              label: "Current",
              value: lineageQuery.data.currentIssuer ? (
                <Link
                  params={{ id: lineageQuery.data.currentIssuer.id }}
                  search={{ validAt: undefined }}
                  to="/legal-entity/$id"
                >
                  {lineageQuery.data.currentIssuer.name}
                </Link>
              ) : (
                "None"
              ),
            },
            { label: "Issuer lineage", value: `${lineageQuery.data.issuerLineage.length} nodes` },
            { label: "Resolved at", value: formatTimestamp(lineageQuery.data.resolvedAt) },
          ]}
          title="Issuer context"
        />
        <TimeControls
          onApply={(next) =>
            navigate({
              to: "/security/$id",
              params: { id },
              search: { validAt: next.validAt },
            })
          }
          validAt={validAt}
        />
        <Link
          className="btn-secondary"
          params={{ id }}
          search={{ validAt, knownAt: undefined }}
          style={{ textAlign: "center", display: "block", padding: "10px 16px" }}
          to="/security/$id/audit"
        >
          Open Audit Comparison
        </Link>
      </aside>

      <main className="workspace-main">
        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Lineage Graph</div>
              <h2 className="panel-title">Security and issuer ancestry</h2>
            </div>
            <div className="pill-row">
              <span className="pill pill-active">{lineageQuery.data.security.type}</span>
              <span className={`pill ${lineageQuery.data.security.active ? "" : "pill-terminal"}`}>
                {lineageQuery.data.security.state}
              </span>
            </div>
          </div>
          <LineageGraph edges={graph.edges} nodes={graph.nodes} />
        </section>

        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Timeline</div>
              <h2 className="panel-title">Corporate action feed</h2>
            </div>
          </div>
          <TimelineFeed entries={actionsQuery.data} />
        </section>

        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Name History</div>
              <h2 className="panel-title">Recorded renames</h2>
            </div>
          </div>
          <NameHistoryTable nameHistory={lineageQuery.data.nameHistory} />
        </section>
      </main>

      <aside className="workspace-rail">
        <SummaryCard
          eyebrow="Current View"
          rows={[
            { label: "Security lineage", value: `${lineageQuery.data.securityLineage.length} nodes` },
            { label: "Issuer lineage", value: `${lineageQuery.data.issuerLineage.length} nodes` },
            { label: "Action feed", value: `${actionsQuery.data.length} events` },
          ]}
          title={validAt ? `State at ${formatDateOnly(validAt)}` : "Latest state"}
        />
        <JsonDrawer label="Security lineage payload" value={lineageQuery.data} />
      </aside>
    </div>
  );
}
