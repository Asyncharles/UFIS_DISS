const AUDIT_HINTS_KEY = "ufis.audit-hint-timestamps";

export function saveAuditHints(timestamps: string[]) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(AUDIT_HINTS_KEY, JSON.stringify(timestamps));
}

export function loadAuditHints(): string[] {
  if (typeof window === "undefined") return [];
  const raw = window.localStorage.getItem(AUDIT_HINTS_KEY);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}
