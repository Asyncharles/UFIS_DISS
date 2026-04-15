import {
  SearchCorporateActionResult,
  SearchLegalEntityResult,
  SearchSecurityResult,
} from "../../api/schemas";
import { RecentInvestigation, saveRecentInvestigation } from "../../lib/recent";

export function securityHref(id: string) {
  return `/security/${id}`;
}

export function legalEntityHref(id: string) {
  return `/legal-entity/${id}`;
}

export function corporateActionHref(id: string) {
  return `/corporate-action/${id}`;
}

export function rememberSecurity(result: SearchSecurityResult) {
  remember({
    kind: "security",
    id: result.id,
    label: result.name,
    subtitle: result.isin ?? result.issuerName ?? result.type,
    href: securityHref(result.id),
  });
}

export function rememberLegalEntity(result: SearchLegalEntityResult) {
  remember({
    kind: "legalEntity",
    id: result.id,
    label: result.name,
    subtitle: result.type,
    href: legalEntityHref(result.id),
  });
}

export function rememberCorporateAction(result: SearchCorporateActionResult) {
  remember({
    kind: "corporateAction",
    id: result.id,
    label: result.type,
    subtitle: result.description,
    href: corporateActionHref(result.id),
  });
}

export function remember(item: Omit<RecentInvestigation, "viewedAt">) {
  saveRecentInvestigation(item);
}
