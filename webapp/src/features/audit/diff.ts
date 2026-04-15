import { SecurityLineageResponse } from "../../api/schemas";
import { formatDateOnly } from "../../lib/format";

export type AuditDifference = {
  label: string;
  standardValue: string;
  auditValue: string;
  explanation: string;
};

export type NodeDifference = {
  id: string;
  name: string;
  type: string;
  state: string;
  actionType: string;
  actionDate: string;
  category: "security" | "issuer";
  status: "matched" | "standard-only" | "audit-only";
  explanation: string;
};

export type NodeComparison = {
  securityNodes: NodeDifference[];
  issuerNodes: NodeDifference[];
};

export type UnknownAction = {
  actionId: string;
  actionType: string;
  actionDate: string;
  parentName: string;
  category: "security" | "issuer";
};

export type AuditAnalysis = {
  differences: AuditDifference[];
  nodeComparison: NodeComparison;
  unknownActions: UnknownAction[];
  summary: string;
};

export function buildAuditAnalysis(
  standard: SecurityLineageResponse,
  audit: SecurityLineageResponse,
): AuditAnalysis {
  const differences = buildAuditDifferences(standard, audit);
  const nodeComparison = buildNodeComparison(standard, audit);
  const unknownActions = buildUnknownActions(standard, audit);
  const summary = buildSummary(differences, nodeComparison, unknownActions);

  return { differences, nodeComparison, unknownActions, summary };
}

function buildSummary(
  differences: AuditDifference[],
  nodeComparison: NodeComparison,
  unknownActions: UnknownAction[],
): string {
  if (differences.length === 0 && unknownActions.length === 0) {
    return "No divergence. The system's knowledge at the audit point was complete for this security.";
  }

  const parts: string[] = [];

  if (unknownActions.length > 0) {
    const types = [...new Set(unknownActions.map((a) => a.actionType.replace("_", " ").toLowerCase()))];
    parts.push(
      `${unknownActions.length} corporate action${unknownActions.length > 1 ? "s were" : " was"} not yet recorded at the audit point: ${types.join(", ")}.`,
    );
  }

  const missingSecNodes = nodeComparison.securityNodes.filter((n) => n.status === "standard-only");
  const missingIssuerNodes = nodeComparison.issuerNodes.filter((n) => n.status === "standard-only");

  if (missingSecNodes.length > 0) {
    parts.push(
      `${missingSecNodes.length} security lineage node${missingSecNodes.length > 1 ? "s are" : " is"} missing from the audit view.`,
    );
  }
  if (missingIssuerNodes.length > 0) {
    parts.push(
      `${missingIssuerNodes.length} issuer lineage node${missingIssuerNodes.length > 1 ? "s are" : " is"} missing from the audit view.`,
    );
  }

  const issuerDiff = differences.find((d) => d.label === "Current issuer");
  if (issuerDiff) {
    parts.push(
      `The issuer resolved differently: "${issuerDiff.auditValue}" at audit time vs "${issuerDiff.standardValue}" with full knowledge.`,
    );
  }

  const stateDiff = differences.find((d) => d.label === "Security state");
  if (stateDiff) {
    parts.push(
      `The security's state was "${stateDiff.auditValue}" at audit time but is now "${stateDiff.standardValue}".`,
    );
  }

  return parts.join(" ");
}

export function buildNodeComparison(
  standard: SecurityLineageResponse,
  audit: SecurityLineageResponse,
): NodeComparison {
  const standardSecMap = new Map(
    standard.securityLineage.map((e) => [e.parentId, e]),
  );
  const auditSecMap = new Map(
    audit.securityLineage.map((e) => [e.parentId, e]),
  );

  const securityNodes: NodeDifference[] = [];

  for (const [id, entry] of standardSecMap) {
    const inAudit = auditSecMap.has(id);
    securityNodes.push({
      id,
      name: entry.parentName,
      type: entry.parentType,
      state: entry.parentState,
      actionType: entry.actionType.replace("_", " "),
      actionDate: formatDateOnly(entry.actionDate),
      category: "security",
      status: inAudit ? "matched" : "standard-only",
      explanation: inAudit
        ? "This lineage node is visible in both resolutions."
        : `Not visible at audit time. The ${entry.actionType.replace("_", " ").toLowerCase()} on ${formatDateOnly(entry.actionDate)} was not yet recorded.`,
    });
  }
  for (const [id, entry] of auditSecMap) {
    if (!standardSecMap.has(id)) {
      securityNodes.push({
        id,
        name: entry.parentName,
        type: entry.parentType,
        state: entry.parentState,
        actionType: entry.actionType.replace("_", " "),
        actionDate: formatDateOnly(entry.actionDate),
        category: "security",
        status: "audit-only",
        explanation: "This node appears in the audit view but not in the standard resolution. This may indicate a retroactive correction.",
      });
    }
  }

  const standardEntMap = new Map(
    standard.issuerLineage.map((e) => [e.parentId, e]),
  );
  const auditEntMap = new Map(
    audit.issuerLineage.map((e) => [e.parentId, e]),
  );

  const issuerNodes: NodeDifference[] = [];

  for (const [id, entry] of standardEntMap) {
    const inAudit = auditEntMap.has(id);
    issuerNodes.push({
      id,
      name: entry.parentName,
      type: entry.parentType,
      state: entry.parentState,
      actionType: entry.actionType.replace("_", " "),
      actionDate: formatDateOnly(entry.actionDate),
      category: "issuer",
      status: inAudit ? "matched" : "standard-only",
      explanation: inAudit
        ? "This lineage node is visible in both resolutions."
        : `Not visible at audit time. The ${entry.actionType.replace("_", " ").toLowerCase()} on ${formatDateOnly(entry.actionDate)} was not yet recorded.`,
    });
  }
  for (const [id, entry] of auditEntMap) {
    if (!standardEntMap.has(id)) {
      issuerNodes.push({
        id,
        name: entry.parentName,
        type: entry.parentType,
        state: entry.parentState,
        actionType: entry.actionType.replace("_", " "),
        actionDate: formatDateOnly(entry.actionDate),
        category: "issuer",
        status: "audit-only",
        explanation: "This node appears in the audit view but not in the standard resolution.",
      });
    }
  }

  return { securityNodes, issuerNodes };
}

export function buildAuditDifferences(
  standard: SecurityLineageResponse,
  audit: SecurityLineageResponse,
) {
  const differences: AuditDifference[] = [];

  compare(differences, "Security name",
    standard.security.name, audit.security.name,
    "The security's name was changed by a corporate action not yet recorded at the audit point.");

  compare(differences, "Security ISIN",
    standard.security.isin ?? "None", audit.security.isin ?? "None",
    "The ISIN was updated by an action not yet recorded at the audit point.");

  compare(differences, "Security state",
    standard.security.state, audit.security.state,
    "A state-changing action (merger, split, redemption) was not yet recorded at the audit point.");

  compare(differences, "Current issuer",
    standard.currentIssuer?.name ?? "None", audit.currentIssuer?.name ?? "None",
    "An issuer-affecting action (acquisition, merger) was not yet recorded, so the security resolves to a different issuer.");

  compare(differences, "Security lineage depth",
    `${standard.securityLineage.length} nodes`,
    `${audit.securityLineage.length} nodes`,
    "Corporate actions that create lineage relationships were not yet recorded at the audit point.");

  compare(differences, "Issuer lineage depth",
    `${standard.issuerLineage.length} nodes`,
    `${audit.issuerLineage.length} nodes`,
    "Issuer-affecting corporate actions were not yet recorded at the audit point.");

  compare(differences, "Security name events",
    `${standard.nameHistory.security.length}`,
    `${audit.nameHistory.security.length}`,
    "Name change actions were not yet recorded at the audit point.");

  compare(differences, "Issuer name events",
    `${standard.nameHistory.issuer.length}`,
    `${audit.nameHistory.issuer.length}`,
    "Issuer name change actions were not yet recorded at the audit point.");

  return differences;
}

function buildUnknownActions(
  standard: SecurityLineageResponse,
  audit: SecurityLineageResponse,
): UnknownAction[] {
  const auditSecActionIds = new Set(audit.securityLineage.map((e) => e.actionId));
  const auditEntActionIds = new Set(audit.issuerLineage.map((e) => e.actionId));

  const unknown: UnknownAction[] = [];

  for (const entry of standard.securityLineage) {
    if (!auditSecActionIds.has(entry.actionId)) {
      unknown.push({
        actionId: entry.actionId,
        actionType: entry.actionType,
        actionDate: entry.actionDate,
        parentName: entry.parentName,
        category: "security",
      });
    }
  }

  for (const entry of standard.issuerLineage) {
    if (!auditEntActionIds.has(entry.actionId)) {
      unknown.push({
        actionId: entry.actionId,
        actionType: entry.actionType,
        actionDate: entry.actionDate,
        parentName: entry.parentName,
        category: "issuer",
      });
    }
  }

  // Deduplicate by actionId
  const seen = new Set<string>();
  return unknown.filter((a) => {
    if (seen.has(a.actionId)) return false;
    seen.add(a.actionId);
    return true;
  });
}

function compare(
  differences: AuditDifference[],
  label: string,
  standardValue: string,
  auditValue: string,
  explanation: string,
) {
  if (standardValue !== auditValue) {
    differences.push({ label, standardValue, auditValue, explanation });
  }
}
