import { createRootRoute, createRoute, createRouter } from "@tanstack/react-router";

import { AppShell } from "./AppShell";
import { BrowsePage } from "../routes/BrowsePage";
import { CorporateActionPage } from "../routes/CorporateActionPage";
import { HomePage } from "../routes/HomePage";
import { LegalEntityPage } from "../routes/LegalEntityPage";
import { SearchPage } from "../routes/SearchPage";
import { SecurityAuditPage } from "../routes/SecurityAuditPage";
import { SecurityPage } from "../routes/SecurityPage";

function stringSearchParam(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

const rootRoute = createRootRoute({
  component: AppShell,
});

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: HomePage,
});

const searchRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/search",
  validateSearch: (search: Record<string, unknown>) => ({
    q: typeof search.q === "string" ? search.q : "",
  }),
  component: () => {
    const search = searchRoute.useSearch();
    return <SearchPage query={search.q} />;
  },
});

const browseRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/browse",
  component: BrowsePage,
});

const securityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/security/$id",
  validateSearch: (search: Record<string, unknown>) => ({
    validAt: stringSearchParam(search.validAt),
  }),
  component: () => {
    const params = securityRoute.useParams();
    const search = securityRoute.useSearch();
    return <SecurityPage id={params.id} validAt={search.validAt} />;
  },
});

const securityAuditRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/security/$id/audit",
  validateSearch: (search: Record<string, unknown>) => ({
    validAt: stringSearchParam(search.validAt),
    knownAt: stringSearchParam(search.knownAt),
  }),
  component: () => {
    const params = securityAuditRoute.useParams();
    const search = securityAuditRoute.useSearch();
    return <SecurityAuditPage id={params.id} knownAt={search.knownAt} validAt={search.validAt} />;
  },
});

const legalEntityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/legal-entity/$id",
  validateSearch: (search: Record<string, unknown>) => ({
    validAt: stringSearchParam(search.validAt),
  }),
  component: () => {
    const params = legalEntityRoute.useParams();
    const search = legalEntityRoute.useSearch();
    return <LegalEntityPage id={params.id} validAt={search.validAt} />;
  },
});

const corporateActionRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/corporate-action/$id",
  component: () => {
    const params = corporateActionRoute.useParams();
    return <CorporateActionPage id={params.id} />;
  },
});

const routeTree = rootRoute.addChildren([
  indexRoute,
  searchRoute,
  browseRoute,
  securityRoute,
  securityAuditRoute,
  legalEntityRoute,
  corporateActionRoute,
]);

export const router = createRouter({
  routeTree,
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
