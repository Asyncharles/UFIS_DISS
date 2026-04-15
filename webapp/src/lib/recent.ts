export type RecentInvestigationKind = "security" | "legalEntity" | "corporateAction";

export type RecentInvestigation = {
  kind: RecentInvestigationKind;
  id: string;
  label: string;
  subtitle?: string;
  href: string;
  viewedAt: string;
};

const RECENT_KEY = "ufis.recent-investigations";
const MAX_ITEMS = 8;

export function loadRecentInvestigations() {
  if (typeof window === "undefined") {
    return [] as RecentInvestigation[];
  }

  const raw = window.localStorage.getItem(RECENT_KEY);
  if (!raw) {
    return [] as RecentInvestigation[];
  }

  try {
    const parsed = JSON.parse(raw) as RecentInvestigation[];
    if (!Array.isArray(parsed)) {
      return [] as RecentInvestigation[];
    }
    return parsed;
  } catch {
    return [] as RecentInvestigation[];
  }
}

export function saveRecentInvestigation(item: Omit<RecentInvestigation, "viewedAt">) {
  if (typeof window === "undefined") {
    return;
  }

  const existing = loadRecentInvestigations().filter(
    (candidate) => !(candidate.kind === item.kind && candidate.id === item.id),
  );
  const next = [{ ...item, viewedAt: new Date().toISOString() }, ...existing].slice(0, MAX_ITEMS);
  window.localStorage.setItem(RECENT_KEY, JSON.stringify(next));
}
