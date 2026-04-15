import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";

import { api } from "../api/client";
import { useBreadcrumbLabel } from "../app/BreadcrumbContext";
import { JsonDrawer } from "../components/JsonDrawer";
import { remember } from "../features/search/search-links";
import { formatDateOnly, formatIdentifier } from "../lib/format";

type CorporateActionPageProps = {
  id: string;
};

export function CorporateActionPage({ id }: CorporateActionPageProps) {
  const { setLabel } = useBreadcrumbLabel();
  const detailQuery = useQuery({
    queryKey: ["corporate-action-detail", id],
    queryFn: () => api.getCorporateActionDetail(id),
  });

  useEffect(() => {
    if (!detailQuery.data) return;
    setLabel(`action-${id}`, detailQuery.data.action.type.replace("_", " "));
    remember({
      kind: "corporateAction",
      id: detailQuery.data.action.id,
      label: detailQuery.data.action.type,
      subtitle: detailQuery.data.action.description,
      href: `/corporate-action/${detailQuery.data.action.id}`,
    });
  }, [detailQuery.data]);

  if (detailQuery.isLoading) {
    return <div className="loading-text">Loading corporate action...</div>;
  }

  if (detailQuery.error || !detailQuery.data) {
    return (
      <div className="error-panel">
        <div className="error-message">
          {detailQuery.error instanceof Error ? detailQuery.error.message : "Failed to load corporate action."}
        </div>
        <div className="error-hint">Check the action ID and ensure the backend is running.</div>
      </div>
    );
  }

  const { action, sourceSecurities, resultSecurities, terminatedSecurities, sourceEntities, resultEntities, lineageRecords } = detailQuery.data;

  const hasSourceData = sourceSecurities.length > 0 || sourceEntities.length > 0;
  const hasResultData = resultSecurities.length > 0 || resultEntities.length > 0;
  const hasTerminated = terminatedSecurities.length > 0;

  return (
    <div className="page-grid">
      <section className="panel action-header">
        <div className="panel-kicker">Corporate Action</div>
        <div className="action-type">{action.type.replace("_", " ")}</div>
        <div className="action-desc">{action.description}</div>
        <div className="pill-row" style={{ marginTop: 12 }}>
          <span className="pill pill-historical">{formatDateOnly(action.validDate)}</span>
          {action.splitRatio ? <span className="pill">Ratio: {action.splitRatio}</span> : null}
          <span className="pill mono">{formatIdentifier(action.id)}</span>
        </div>
      </section>

      {hasSourceData || hasResultData ? (
        <div className="action-flow">
          {hasSourceData ? (
            <section className="panel panel-padding">
              <div className="panel-kicker">Source</div>
              <div className="stack">
                {sourceEntities.map((entity) => (
                  <Link
                    className="entity-card"
                    key={entity.id}
                    params={{ id: entity.id }}
                    search={{ validAt: undefined }}
                    to="/legal-entity/$id"
                  >
                    <div className="entity-card-name">{entity.name}</div>
                    <div className="pill-row">
                      <span className="pill pill-active">{entity.type}</span>
                      <span className={`pill ${entity.active ? "" : "pill-terminal"}`}>{entity.state}</span>
                    </div>
                  </Link>
                ))}
                {sourceSecurities.map((sec) => (
                  <Link
                    className="entity-card"
                    key={sec.id}
                    params={{ id: sec.id }}
                    search={{ validAt: undefined }}
                    to="/security/$id"
                  >
                    <div className="entity-card-name">{sec.name}</div>
                    <div className="pill-row">
                      <span className="pill pill-active">{sec.type}</span>
                      <span className={`pill ${sec.active ? "" : "pill-terminal"}`}>{sec.state}</span>
                      {sec.isin ? <span className="pill mono">{sec.isin}</span> : null}
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          ) : null}

          {hasSourceData && hasResultData ? (
            <div className="flow-arrow-col">
              <div className="flow-arrow" />
            </div>
          ) : null}

          {hasResultData ? (
            <section className="panel panel-padding">
              <div className="panel-kicker">Result</div>
              <div className="stack">
                {resultEntities.map((entity) => (
                  <Link
                    className="entity-card"
                    key={entity.id}
                    params={{ id: entity.id }}
                    search={{ validAt: undefined }}
                    to="/legal-entity/$id"
                  >
                    <div className="entity-card-name">{entity.name}</div>
                    <div className="pill-row">
                      <span className="pill pill-active">{entity.type}</span>
                      <span className={`pill ${entity.active ? "" : "pill-terminal"}`}>{entity.state}</span>
                    </div>
                  </Link>
                ))}
                {resultSecurities.map((sec) => (
                  <Link
                    className="entity-card"
                    key={sec.id}
                    params={{ id: sec.id }}
                    search={{ validAt: undefined }}
                    to="/security/$id"
                  >
                    <div className="entity-card-name">{sec.name}</div>
                    <div className="pill-row">
                      <span className="pill pill-active">{sec.type}</span>
                      <span className={`pill ${sec.active ? "" : "pill-terminal"}`}>{sec.state}</span>
                      {sec.isin ? <span className="pill mono">{sec.isin}</span> : null}
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          ) : null}
        </div>
      ) : (
        <div className="empty-state">
          No lineage records found for this action. This may be a name change or other non-lineage action.
        </div>
      )}

      {hasTerminated ? (
        <section className="panel panel-padding" style={{ borderLeft: "3px solid var(--color-terminal)" }}>
          <div className="panel-kicker">Terminated</div>
          <div className="stack">
            {terminatedSecurities.map((sec) => (
              <Link
                className="entity-card"
                key={sec.id}
                params={{ id: sec.id }}
                search={{ validAt: undefined }}
                to="/security/$id"
              >
                <div className="entity-card-name">{sec.name}</div>
                <div className="pill-row">
                  <span className="pill pill-terminal">{sec.state}</span>
                  {sec.isin ? <span className="pill mono">{sec.isin}</span> : null}
                </div>
              </Link>
            ))}
          </div>
        </section>
      ) : null}

      {lineageRecords.length > 0 ? (
        <section className="panel panel-padding">
          <div className="panel-kicker">Lineage Records</div>
          <table className="data-table">
            <thead>
              <tr>
                <th>Lineage ID</th>
                <th>Parent</th>
                <th>Child</th>
                <th>Type</th>
              </tr>
            </thead>
            <tbody>
              {lineageRecords.map((record) => (
                <tr key={record.id}>
                  <td className="mono text-muted">{formatIdentifier(record.id)}</td>
                  <td className="mono">{record.parentSecurityId ? formatIdentifier(record.parentSecurityId) : record.parentEntityId ? formatIdentifier(record.parentEntityId) : "N/A"}</td>
                  <td className="mono">{record.childSecurityId ? formatIdentifier(record.childSecurityId) : record.childEntityId ? formatIdentifier(record.childEntityId) : "Terminal"}</td>
                  <td>{record.parentSecurityId ? "Security" : "Entity"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}

      <JsonDrawer label="Corporate action detail payload" value={detailQuery.data} />
    </div>
  );
}
