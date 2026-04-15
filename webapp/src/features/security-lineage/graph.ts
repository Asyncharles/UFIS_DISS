import { Edge, MarkerType, Node } from "reactflow";

import { SecurityLineageResponse } from "../../api/schemas";
import { formatDateOnly } from "../../lib/format";

type GraphData = {
  nodes: Node[];
  edges: Edge[];
};

export function buildSecurityGraph(data: SecurityLineageResponse): GraphData {
  const nodes: Node[] = [
    {
      id: `security-${data.security.id}`,
      position: { x: 40, y: 72 },
      data: {
        label: data.security.name,
        meta: `${data.security.type} \u00b7 ${data.security.state}`,
        tone: "focused",
      },
      type: "lineage",
    },
  ];

  const edges: Edge[] = [];

  if (data.currentIssuer) {
    nodes.push({
      id: `issuer-${data.currentIssuer.id}`,
      position: { x: 480, y: 72 },
      data: {
        label: data.currentIssuer.name,
        meta: `${data.currentIssuer.type} \u00b7 ${data.currentIssuer.state}`,
        tone: data.currentIssuer.active ? "active" : "terminal",
      },
      type: "lineage",
    });

    edges.push({
      id: `security-issuer-${data.security.id}`,
      source: `security-${data.security.id}`,
      target: `issuer-${data.currentIssuer.id}`,
      label: "issued by",
      animated: false,
      style: { strokeDasharray: "6 3" },
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
    });
  }

  data.securityLineage.forEach((entry, index) => {
    nodes.push({
      id: `security-parent-${entry.parentId}`,
      position: { x: 40, y: 196 + index * 116 },
      data: {
        label: entry.parentName,
        meta: `${entry.actionType.toLowerCase().replace("_", " ")} on ${formatDateOnly(entry.actionDate)}`,
        tone: entry.parentActive ? "historical" : "terminal",
      },
      type: "lineage",
    });

    edges.push({
      id: `security-edge-${entry.parentId}-${data.security.id}`,
      source: `security-parent-${entry.parentId}`,
      target: `security-${data.security.id}`,
      label: entry.actionType.toLowerCase().replace("_", " "),
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
    });
  });

  data.issuerLineage.forEach((entry, index) => {
    if (!data.currentIssuer) {
      return;
    }

    nodes.push({
      id: `issuer-parent-${entry.parentId}`,
      position: { x: 480, y: 196 + index * 116 },
      data: {
        label: entry.parentName,
        meta: `${entry.actionType.toLowerCase().replace("_", " ")} on ${formatDateOnly(entry.actionDate)}`,
        tone: entry.parentActive ? "historical" : "terminal",
      },
      type: "lineage",
    });

    edges.push({
      id: `issuer-edge-${entry.parentId}-${data.currentIssuer.id}`,
      source: `issuer-parent-${entry.parentId}`,
      target: `issuer-${data.currentIssuer.id}`,
      label: entry.actionType.toLowerCase().replace("_", " "),
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
    });
  });

  return { nodes, edges };
}
