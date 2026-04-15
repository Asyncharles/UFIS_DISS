import { useNavigate } from "@tanstack/react-router";

import { SearchResponse } from "../api/schemas";
import { formatDateOnly, formatIdentifier } from "../lib/format";
import {
  rememberCorporateAction,
  rememberLegalEntity,
  rememberSecurity,
} from "../features/search/search-links";

type SearchResultsProps = {
  query: string;
  results: SearchResponse;
};

export function SearchResults({ query, results }: SearchResultsProps) {
  const navigate = useNavigate();
  const hasResults =
    results.securities.length > 0 ||
    results.legalEntities.length > 0 ||
    results.corporateActions.length > 0;

  if (!hasResults) {
    return (
      <div className="empty-state">
        No results for <span className="mono">"{query}"</span>. Try a UUID, ISIN prefix, or partial name.
      </div>
    );
  }

  return (
    <div className="search-groups">
      {results.securities.length > 0 ? (
        <section className="panel search-section">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Securities</div>
              <h2 className="panel-title">Instrument matches</h2>
            </div>
            <span className="pill">{results.securities.length}</span>
          </div>
          <div className="search-list">
            {results.securities.map((result) => (
              <button
                className="result-row"
                key={result.id}
                onClick={() => {
                  rememberSecurity(result);
                  navigate({
                    to: "/security/$id",
                    params: { id: result.id },
                    search: { validAt: undefined },
                  });
                }}
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
            ))}
          </div>
        </section>
      ) : null}

      {results.legalEntities.length > 0 ? (
        <section className="panel search-section">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Legal Entities</div>
              <h2 className="panel-title">Issuer matches</h2>
            </div>
            <span className="pill">{results.legalEntities.length}</span>
          </div>
          <div className="search-list">
            {results.legalEntities.map((result) => (
              <button
                className="result-row"
                key={result.id}
                onClick={() => {
                  rememberLegalEntity(result);
                  navigate({
                    to: "/legal-entity/$id",
                    params: { id: result.id },
                    search: { validAt: undefined },
                  });
                }}
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
            ))}
          </div>
        </section>
      ) : null}

      {results.corporateActions.length > 0 ? (
        <section className="panel search-section">
          <div className="panel-header">
            <div>
              <div className="panel-kicker">Corporate Actions</div>
              <h2 className="panel-title">Event matches</h2>
            </div>
            <span className="pill">{results.corporateActions.length}</span>
          </div>
          <div className="search-list">
            {results.corporateActions.map((result) => (
              <button
                className="result-row"
                key={result.id}
                onClick={() => {
                  rememberCorporateAction(result);
                  navigate({
                    to: "/corporate-action/$id",
                    params: { id: result.id },
                  });
                }}
                type="button"
              >
                <div>
                  <div className="result-name">{result.type.replace("_", " ")}</div>
                  <div className="result-id">{formatIdentifier(result.id)}</div>
                </div>
                <div className="pill-row">
                  <span className="pill pill-historical">{formatDateOnly(result.validDate)}</span>
                </div>
                <div style={{ fontSize: "0.8rem", color: "var(--text-secondary)" }}>
                  {result.description}
                </div>
              </button>
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}
