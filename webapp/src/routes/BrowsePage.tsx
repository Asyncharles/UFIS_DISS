import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";

import { api } from "../api/client";
import { formatIdentifier } from "../lib/format";

type Category = "securities" | "entities";

export function BrowsePage() {
  const navigate = useNavigate();
  const [category, setCategory] = useState<Category>("securities");
  const [typeFilter, setTypeFilter] = useState("");
  const [stateFilter, setStateFilter] = useState("");

  const securitiesQuery = useQuery({
    queryKey: ["browse-securities", typeFilter, stateFilter],
    queryFn: () => api.browseSecurities(typeFilter || undefined, stateFilter || undefined),
    enabled: category === "securities",
  });

  const entitiesQuery = useQuery({
    queryKey: ["browse-entities", typeFilter, stateFilter],
    queryFn: () => api.browseLegalEntities(typeFilter || undefined, stateFilter || undefined),
    enabled: category === "entities",
  });

  function handleCategoryChange(next: Category) {
    setCategory(next);
    setTypeFilter("");
    setStateFilter("");
  }

  const securityTypes = ["EQUITY", "BOND"];
  const securityStates = ["ACTIVE", "MERGED", "SPLIT", "REDEEMED"];
  const entityTypes = ["COMPANY", "FUND", "SPV", "GOVERNMENT", "SUPRANATIONAL"];
  const entityStates = ["ACTIVE", "MERGED", "ACQUIRED"];

  const types = category === "securities" ? securityTypes : entityTypes;
  const states = category === "securities" ? securityStates : entityStates;

  const activeQuery = category === "securities" ? securitiesQuery : entitiesQuery;

  return (
    <div className="page-grid">
      <section className="panel panel-padding">
        <div className="panel-kicker">Browse</div>
        <h1 className="panel-title">Explore the UFIS graph</h1>

        <div className="browse-controls" style={{ marginTop: 16 }}>
          <label>
            <span className="meta">Category</span>
            <select
              className="browse-select"
              onChange={(e) => handleCategoryChange(e.target.value as Category)}
              value={category}
            >
              <option value="securities">Securities</option>
              <option value="entities">Legal Entities</option>
            </select>
          </label>
          <label>
            <span className="meta">Type</span>
            <select
              className="browse-select"
              onChange={(e) => setTypeFilter(e.target.value)}
              value={typeFilter}
            >
              <option value="">All types</option>
              {types.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            <span className="meta">State</span>
            <select
              className="browse-select"
              onChange={(e) => setStateFilter(e.target.value)}
              value={stateFilter}
            >
              <option value="">All states</option>
              {states.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </label>
        </div>
      </section>

      {activeQuery.isLoading ? (
        <div className="loading-text">Loading...</div>
      ) : activeQuery.error ? (
        <div className="error-panel">
          <div className="error-message">
            {activeQuery.error instanceof Error ? activeQuery.error.message : "Browse failed."}
          </div>
          <div className="error-hint">Check that the backend is running.</div>
        </div>
      ) : activeQuery.data && activeQuery.data.length === 0 ? (
        <div className="empty-state">No results match the current filters.</div>
      ) : activeQuery.data ? (
        <section className="panel panel-padding">
          <div className="panel-kicker">
            {activeQuery.data.length} {category === "securities" ? "securities" : "entities"}
          </div>
          <div className="search-list">
            {category === "securities" && securitiesQuery.data
              ? securitiesQuery.data.map((result) => (
                  <button
                    className="result-row"
                    key={result.id}
                    onClick={() =>
                      navigate({
                        to: "/security/$id",
                        params: { id: result.id },
                        search: { validAt: undefined },
                      })
                    }
                    type="button"
                  >
                    <div>
                      <div className="result-name">{result.name}</div>
                      <div className="result-id">{formatIdentifier(result.id)}</div>
                    </div>
                    <div className="pill-row">
                      <span className="pill pill-active">{result.type}</span>
                      <span className={`pill ${result.active ? "" : "pill-terminal"}`}>{result.state}</span>
                    </div>
                    <div className="pill-row">
                      {result.isin ? <span className="pill mono">{result.isin}</span> : null}
                      {result.issuerName ? <span className="pill">{result.issuerName}</span> : null}
                    </div>
                  </button>
                ))
              : null}
            {category === "entities" && entitiesQuery.data
              ? entitiesQuery.data.map((result) => (
                  <button
                    className="result-row"
                    key={result.id}
                    onClick={() =>
                      navigate({
                        to: "/legal-entity/$id",
                        params: { id: result.id },
                        search: { validAt: undefined },
                      })
                    }
                    type="button"
                  >
                    <div>
                      <div className="result-name">{result.name}</div>
                      <div className="result-id">{formatIdentifier(result.id)}</div>
                    </div>
                    <div className="pill-row">
                      <span className="pill pill-active">{result.type}</span>
                      <span className={`pill ${result.active ? "" : "pill-terminal"}`}>{result.state}</span>
                    </div>
                    <div />
                  </button>
                ))
              : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}
