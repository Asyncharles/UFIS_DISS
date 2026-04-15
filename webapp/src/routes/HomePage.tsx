import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";

import { api } from "../api/client";
import { ActionFeed } from "../components/ActionFeed";
import { GlobalSearch } from "../components/GlobalSearch";
import { saveAuditHints } from "../lib/audit-hints";
import { loadRecentInvestigations } from "../lib/recent";

export function HomePage() {
  const navigate = useNavigate();
  const recentItems = loadRecentInvestigations();
  const [tier, setTier] = useState<"SMALL" | "MEDIUM" | "LARGE">("SMALL");
  const recentActionsQuery = useQuery({
    queryKey: ["recent-actions"],
    queryFn: () => api.getRecentActions(10),
  });

  const seedMutation = useMutation({
    mutationFn: () => api.seedDummyData(tier),
    onSuccess: (result) => {
      if (result.auditHintTimestamps.length > 0) {
        saveAuditHints(result.auditHintTimestamps);
      }
      if (result.sampleSecurityId) {
        navigate({
          to: "/security/$id",
          params: { id: result.sampleSecurityId },
          search: { validAt: undefined },
        });
        return;
      }
      navigate({
        to: "/search",
        search: { q: result.searchHint },
      });
    },
  });

  return (
    <div className="page-grid">
      <div className="home-header">
        <div>
          <h1 className="home-title">Trace what was true,<br />when it was true.</h1>
          <div className="home-desc">
            Search securities, issuers, and corporate actions to begin an investigation.
          </div>
        </div>
        <div className="home-search">
          <GlobalSearch
            autoFocus
            onSearch={(query) =>
              navigate({
                to: "/search",
                search: { q: query },
              })
            }
            placeholder="UUID, ISIN, issuer name, security name..."
          />
        </div>
      </div>

      <div className="two-col">
        <section className="panel panel-padding">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Recent Investigations</div>
              <h2 className="panel-title">Resume prior work</h2>
            </div>
          </div>
          {recentItems.length === 0 ? (
            <div className="empty-state">
              No investigations yet. Search for a security, issuer, or corporate action to begin.
            </div>
          ) : (
            <div className="recent-list">
              {recentItems.map((item) => (
                <button
                  className="recent-row"
                  key={`${item.kind}-${item.id}`}
                  onClick={() => {
                    if (item.kind === "security") {
                      navigate({
                        to: "/security/$id",
                        params: { id: item.id },
                        search: { validAt: undefined },
                      });
                      return;
                    }
                    if (item.kind === "legalEntity") {
                      navigate({
                        to: "/legal-entity/$id",
                        params: { id: item.id },
                        search: { validAt: undefined },
                      });
                      return;
                    }
                    navigate({ to: "/corporate-action/$id", params: { id: item.id } });
                  }}
                  type="button"
                >
                  <div className="recent-kind">
                    {item.kind === "legalEntity" ? "Issuer" : item.kind === "corporateAction" ? "Action" : "Security"}
                  </div>
                  <div>
                    <div className="recent-label">{item.label}</div>
                    {item.subtitle ? <div className="recent-sub">{item.subtitle}</div> : null}
                  </div>
                  <div className="recent-time">
                    {new Date(item.viewedAt).toLocaleDateString()}
                  </div>
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="panel panel-padding stack">
          <div>
            <div className="panel-kicker">System</div>
            <h2 className="panel-title">Data seeding</h2>
          </div>
          <div className="muted" style={{ fontSize: "0.85rem" }}>
            Populate the local Datomic instance with a deterministic simulator dataset.
          </div>
          <div className="seed-controls">
            <select
              className="seed-select"
              onChange={(e) => setTier(e.target.value as "SMALL" | "MEDIUM" | "LARGE")}
              value={tier}
            >
              <option value="SMALL">Small</option>
              <option value="MEDIUM">Medium</option>
              <option value="LARGE">Large</option>
            </select>
            <button
              className="btn-primary"
              disabled={seedMutation.isPending}
              onClick={() => seedMutation.mutate()}
              type="button"
            >
              {seedMutation.isPending ? "Seeding..." : "Seed Dataset"}
            </button>
          </div>
          {seedMutation.data ? (
            <div className="mono text-muted" style={{ fontSize: "0.75rem" }}>
              {seedMutation.data.initialEntities} entities, {seedMutation.data.initialSecurities} securities,{" "}
              {seedMutation.data.corporateActions} actions
            </div>
          ) : null}
          {seedMutation.error instanceof Error ? (
            <div className="mono" style={{ fontSize: "0.75rem", color: "var(--color-terminal)" }}>
              {seedMutation.error.message}
            </div>
          ) : null}
        </section>
      </div>

      <section className="panel panel-padding">
        <div className="panel-header">
          <div>
            <div className="panel-kicker">Activity Feed</div>
            <h2 className="panel-title">Recent corporate actions</h2>
          </div>
        </div>
        {recentActionsQuery.isLoading ? (
          <div className="loading-text">Loading activity...</div>
        ) : recentActionsQuery.data ? (
          <ActionFeed actions={recentActionsQuery.data} />
        ) : null}
      </section>
    </div>
  );
}
