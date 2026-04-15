import { Link, useLocation } from "@tanstack/react-router";
import { useBreadcrumbLabel } from "../app/BreadcrumbContext";

type Crumb = {
  label: string;
  to?: string;
};

export function Breadcrumbs() {
  const location = useLocation();
  const { labels } = useBreadcrumbLabel();
  const path = location.pathname;

  const crumbs: Crumb[] = [{ label: "Home", to: "/" }];

  if (path === "/") {
    return null;
  }

  if (path === "/search") {
    crumbs.push({ label: "Search" });
  } else if (path === "/browse") {
    crumbs.push({ label: "Browse" });
  } else if (path.match(/^\/security\/[^/]+\/audit$/)) {
    const id = path.replace("/security/", "").replace("/audit", "");
    const name = labels[`security-${id}`];
    crumbs.push({ label: name ? `Security: ${name}` : "Security", to: `/security/${id}` });
    crumbs.push({ label: "Audit" });
  } else if (path.match(/^\/security\/[^/]+$/)) {
    const id = path.replace("/security/", "");
    const name = labels[`security-${id}`];
    crumbs.push({ label: name ? `Security: ${name}` : "Security" });
  } else if (path.match(/^\/legal-entity\/[^/]+$/)) {
    const id = path.replace("/legal-entity/", "");
    const name = labels[`entity-${id}`];
    crumbs.push({ label: name ? `Entity: ${name}` : "Entity" });
  } else if (path.match(/^\/corporate-action\/[^/]+$/)) {
    const id = path.replace("/corporate-action/", "");
    const name = labels[`action-${id}`];
    crumbs.push({ label: name ? `Action: ${name}` : "Action" });
  }

  if (crumbs.length <= 1) return null;

  return (
    <nav className="breadcrumbs">
      {crumbs.map((crumb, i) => (
        <span key={i}>
          {i > 0 ? <span className="breadcrumb-sep"> / </span> : null}
          {crumb.to && i < crumbs.length - 1 ? (
            <Link to={crumb.to}>{crumb.label}</Link>
          ) : (
            <span className="breadcrumb-current">{crumb.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
