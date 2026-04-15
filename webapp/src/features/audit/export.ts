import { SecurityLineageResponse } from "../../api/schemas";
import { formatDateOnly, formatIdentifier } from "../../lib/format";
import { AuditAnalysis } from "./diff";

export function buildAuditReport(
  securityName: string,
  validAt: string,
  knownAt: string,
  standard: SecurityLineageResponse,
  audit: SecurityLineageResponse,
  analysis: AuditAnalysis,
): string {
  const lines: string[] = [];

  lines.push(`# Audit Comparison Report`);
  lines.push(``);
  lines.push(`**Security:** ${securityName}`);
  lines.push(`**Valid at:** ${validAt}`);
  lines.push(`**Known at:** ${knownAt}`);
  lines.push(`**Generated:** ${new Date().toISOString()}`);
  lines.push(``);

  if (analysis.summary) {
    lines.push(`## Analysis`);
    lines.push(``);
    lines.push(analysis.summary);
    lines.push(``);
  }

  lines.push(`## Summary`);
  lines.push(``);
  lines.push(`| Field | Standard | Audit |`);
  lines.push(`|-------|----------|-------|`);
  lines.push(`| Security name | ${standard.security.name} | ${audit.security.name} |`);
  lines.push(`| Security state | ${standard.security.state} | ${audit.security.state} |`);
  lines.push(`| ISIN | ${standard.security.isin ?? "None"} | ${audit.security.isin ?? "None"} |`);
  lines.push(`| Issuer | ${standard.currentIssuer?.name ?? "None"} | ${audit.currentIssuer?.name ?? "None"} |`);
  lines.push(`| Security lineage nodes | ${standard.securityLineage.length} | ${audit.securityLineage.length} |`);
  lines.push(`| Issuer lineage nodes | ${standard.issuerLineage.length} | ${audit.issuerLineage.length} |`);
  lines.push(``);

  if (analysis.differences.length === 0) {
    lines.push(`## Differences`);
    lines.push(``);
    lines.push(`No divergence detected.`);
    lines.push(``);
  } else {
    lines.push(`## Differences (${analysis.differences.length})`);
    lines.push(``);
    for (const d of analysis.differences) {
      lines.push(`### ${d.label}`);
      lines.push(`- **Standard:** ${d.standardValue}`);
      lines.push(`- **Audit:** ${d.auditValue}`);
      lines.push(`- **Explanation:** ${d.explanation}`);
      lines.push(``);
    }
  }

  if (analysis.unknownActions.length > 0) {
    lines.push(`## Actions Not Yet Recorded at Audit Point`);
    lines.push(``);
    lines.push(`| Action Type | Date | Affected | ID |`);
    lines.push(`|-------------|------|----------|----|`);
    for (const a of analysis.unknownActions) {
      lines.push(`| ${a.actionType.replace("_", " ")} | ${formatDateOnly(a.actionDate)} | ${a.parentName} (${a.category}) | ${formatIdentifier(a.actionId)} |`);
    }
    lines.push(``);
  }

  const allNodes = [...analysis.nodeComparison.securityNodes, ...analysis.nodeComparison.issuerNodes];
  const divergentNodes = allNodes.filter((n) => n.status !== "matched");

  if (divergentNodes.length > 0) {
    lines.push(`## Node-level Differences (${divergentNodes.length})`);
    lines.push(``);
    for (const n of divergentNodes) {
      const status = n.status === "standard-only" ? "Standard only" : "Audit only";
      lines.push(`- **${n.name}** (${n.category}) — ${status} — ${n.actionType} on ${n.actionDate}`);
      lines.push(`  ${n.explanation}`);
    }
    lines.push(``);
  }

  return lines.join("\n");
}
