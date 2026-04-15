import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";

import { api } from "../api/client";
import { GlobalSearch } from "../components/GlobalSearch";
import { SearchResults } from "../components/SearchResults";

type SearchPageProps = {
  query: string;
};

export function SearchPage({ query }: SearchPageProps) {
  const navigate = useNavigate();
  const searchQuery = useQuery({
    queryKey: ["search", query],
    queryFn: () => api.search(query),
    enabled: query.length > 0,
  });

  return (
    <div className="page-grid">
      <section className="panel panel-padding stack">
        <div className="panel-kicker">Search</div>
        <h1 className="panel-title">Find a security, issuer, or action</h1>
        <GlobalSearch
          initialValue={query}
          onSearch={(nextQuery) =>
            navigate({
              to: "/search",
              search: { q: nextQuery },
            })
          }
        />
        {!query ? (
          <div className="empty-state">Enter a query to search securities, issuers, and corporate actions.</div>
        ) : null}
        {searchQuery.isLoading ? (
          <div className="loading-text">Searching...</div>
        ) : null}
        {searchQuery.error ? (
          <div className="error-panel">
            <div className="error-message">
              {searchQuery.error instanceof Error ? searchQuery.error.message : "Search failed."}
            </div>
            <div className="error-hint">
              Check that the UFIS backend is running and accessible.
            </div>
          </div>
        ) : null}
        {searchQuery.data ? <SearchResults query={query} results={searchQuery.data} /> : null}
      </section>
    </div>
  );
}
