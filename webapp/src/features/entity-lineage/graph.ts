import { Edge, MarkerType, Node } from "reactflow";

import { LegalEntityLineageResponse } from "../../api/schemas";
import { formatDateOnly } from "../../lib/format";

type GraphData = {
  nodes: Node[];
  edges: Edge[];
};

export function buildLegalEntityGraph(data: LegalEntityLineageResponse): GraphData {
  const nodes: Node[] = [
    {
      id: `entity-${data.legalEntity.id}`,
      position: { x: 48, y: 96 },
      data: {
        label: data.legalEntity.name,
        meta: `${data.legalEntity.type} \u00b7 ${data.legalEntity.state}`,
        tone: "focused",
      },
      type: "lineage",
    },
  ];

  const edges: Edge[] = [];

  data.issuerLineage.forEach((entry, index) => {
    nodes.push({
      id: `entity-parent-${entry.parentId}`,
      position: { x: 48, y: 236 + index * 120 },
      data: {
        label: entry.parentName,
        meta: `${entry.actionType.toLowerCase().replace("_", " ")} on ${formatDateOnly(entry.actionDate)}`,
        tone: entry.parentActive ? "historical" : "terminal",
      },
      type: "lineage",
    });

    edges.push({
      id: `entity-edge-${entry.parentId}-${data.legalEntity.id}`,
      source: `entity-parent-${entry.parentId}`,
      target: `entity-${data.legalEntity.id}`,
      label: entry.actionType.toLowerCase().replace("_", " "),
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
    });
  });

  return { nodes, edges };
}
