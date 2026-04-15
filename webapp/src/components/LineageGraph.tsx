import { Background, Controls, Edge, MiniMap, Node, NodeProps, ReactFlow } from "reactflow";
import clsx from "clsx";

type LineageNodeData = {
  label: string;
  meta: string;
  tone: "focused" | "active" | "historical" | "terminal";
};

type LineageGraphProps = {
  nodes: Node<LineageNodeData>[];
  edges: Edge[];
};

function LineageNode({ data }: NodeProps<LineageNodeData>) {
  return (
    <div className={clsx("flow-node", data.tone)}>
      <div className="flow-node-header">
        <span className="flow-node-dot" />
        <span className="flow-node-label">{data.label}</span>
      </div>
      <div className="flow-node-meta">{data.meta}</div>
    </div>
  );
}

const nodeTypes = {
  lineage: LineageNode,
};

const defaultEdgeOptions = {
  style: {
    stroke: "#475569",
    strokeWidth: 1.5,
  },
};

export function LineageGraph({ nodes, edges }: LineageGraphProps) {
  return (
    <div className="flow-frame">
      <ReactFlow
        defaultEdgeOptions={defaultEdgeOptions}
        edges={edges}
        fitView
        nodes={nodes}
        nodeTypes={nodeTypes}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="rgba(148, 163, 184, 0.04)" gap={24} size={1} />
        <Controls />
        <MiniMap
          maskColor="rgba(10, 14, 18, 0.7)"
          nodeColor={(node) => {
            const tone = node.data?.tone;
            if (tone === "focused" || tone === "active") return "#22d3ee";
            if (tone === "historical") return "#f59e0b";
            if (tone === "terminal") return "#ef4444";
            return "#475569";
          }}
        />
      </ReactFlow>
    </div>
  );
}
