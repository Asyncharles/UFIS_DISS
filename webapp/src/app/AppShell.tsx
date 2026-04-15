import { Link, Outlet, useNavigate } from "@tanstack/react-router";

import { GlobalSearch } from "../components/GlobalSearch";
import { Breadcrumbs } from "../components/Breadcrumbs";

export function AppShell() {
  const navigate = useNavigate();

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/">
          <span className="brand-title">UFIS</span>
          <span className="brand-separator" />
          <span className="brand-subtitle">Security Lineage Investigation</span>
        </Link>
        <GlobalSearch
          onSearch={(query) =>
            navigate({
              to: "/search",
              search: { q: query },
            })
          }
          placeholder="Search..."
        />
        <nav className="top-nav">
          <Link activeProps={{ "aria-current": "page" }} to="/">
            Home
          </Link>
          <Link activeProps={{ "aria-current": "page" }} search={{ q: "" }} to="/search">
            Search
          </Link>
          <Link activeProps={{ "aria-current": "page" }} to="/browse">
            Browse
          </Link>
        </nav>
      </header>
      <Breadcrumbs />
      <div className="app-frame">
        <Outlet />
      </div>
    </div>
  );
}
