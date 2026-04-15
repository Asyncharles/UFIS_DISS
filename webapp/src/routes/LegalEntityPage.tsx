import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";

import { api } from "../api/client";
import { JsonDrawer } from "../components/JsonDrawer";
import { LineageGraph } from "../components/LineageGraph";
import { NameHistoryTable } from "../components/NameHistoryTable";
import { SummaryCard } from "../components/SummaryCard";
import { TimeControls } from "../components/TimeControls";
import { TimelineFeed } from "../components/TimelineFeed";
import { buildLegalEntityGraph } from "../features/entity-lineage/graph";
import { useBreadcrumbLabel } from "../app/BreadcrumbContext";
import { remember } from "../features/search/search-links";
import { formatDateOnly, formatTimestamp } from "../lib/format";

type LegalEntityPageProps = {
  id: string;
  validAt?: string;
};

export function LegalEntityPage({ id, validAt }: LegalEntityPageProps) {
  const navigate = useNavigate();
  const { setLabel } = useBreadcrumbLabel();
  const lineageQuery = useQuery({
    queryKey: ["legal-entity-lineage", id, validAt ?? null],
    queryFn: () => api.getLegalEntityLineage(id, validAt),
  });
  const actionsQuery = useQuery({
    queryKey: ["legal-entity-actions", id],
    queryFn: () => api.getLegalEntityActions(id),
  });

  useEffect(() => {
    if (!lineageQuery.data) return;
    setLabel(`entity-${id}`, lineageQuery.data.legalEntity.name);
    remember({
      kind: "legalEntity",
      id: lineageQuery.data.legalEntity.id,
      label: lineageQuery.data.legalEntity.name,
      subtitle: lineageQuery.data.legalEntity.type,
      href: `/legal-entity/${lineageQuery.data.legalEntity.id}`,
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
          : "Failed to load legal entity workspace.";
    return (
      <div className="error-panel">
        <div className="error-message">{message}</div>
        <div className="error-hint">Check the entity ID and ensure the backend is running.</div>
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

  const graph = buildLegalEntityGraph(lineageQuery.data);
  const accentTone = lineageQuery.data.legalEntity.active ? "active" as const : "terminal" as const;

  return (
    <div className="workspace-grid">
      <aside className="workspace-rail">
        <SummaryCard
          accent={accentTone}
          eyebrow="Issuer"
          rows={[
            { label: "Type", value: lineageQuery.data.legalEntity.type },
            { label: "State", value: lineageQuery.data.legalEntity.state },
            { label: "Founded", value: formatDateOnly(lineageQuery.data.legalEntity.foundedDate) },
            { label: "Resolved at", value: formatTimestamp(lineageQuery.data.resolvedAt) },
          ]}
          title={lineageQuery.data.legalEntity.name}
        />
        <TimeControls
          onApply={(next) =>
            navigate({
              to: "/legal-entity/$id",
              params: { id },
              search: { validAt: next.validAt },
            })
          }
          validAt={validAt}
        />
      </aside>

      <main className="workspace-main">
        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Lineage Graph</div>
              <h2 className="panel-title">Issuer ancestry</h2>
            </div>
          </div>
          <LineageGraph edges={graph.edges} nodes={graph.nodes} />
        </section>

        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Timeline</div>
              <h2 className="panel-title">Actions affecting this issuer</h2>
            </div>
          </div>
          <TimelineFeed entries={actionsQuery.data} />
        </section>

        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Name History</div>
              <h2 className="panel-title">Issuer rename history</h2>
            </div>
          </div>
          <NameHistoryTable nameHistory={lineageQuery.data.nameHistory} />
        </section>
      </main>

      <aside className="workspace-rail">
        <SummaryCard
          eyebrow="Current View"
          rows={[
            { label: "Issuer lineage", value: `${lineageQuery.data.issuerLineage.length} nodes` },
            { label: "Action feed", value: `${actionsQuery.data.length} events` },
          ]}
          title={validAt ? `State at ${formatDateOnly(validAt)}` : "Latest state"}
        />
        <JsonDrawer label="Issuer lineage payload" value={lineageQuery.data} />
      </aside>
    </div>
  );
}
