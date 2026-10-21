'use client';

// ArchCanvas — M1.12
// React Flow canvas rendering architecture graph nodes + edges.
// Supports pan/zoom/drag. Clicking a node opens the NodeInspector panel.

import { useCallback, useMemo, useState, useEffect } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  BackgroundVariant,
  Node,
  Edge,
  NodeMouseHandler,
  useNodesState,
  useEdgesState,
  MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { GraphResponse, GraphNode as ApiNode, GraphEdge as ApiEdge } from '../lib/api';
import NodeInspector from './NodeInspector';
import OverlayTable from './OverlayTable';
import CustomNode from './CustomNode';
import SecurityAndAiModal from './SecurityAndAiModal';
import VersionComparisonModal from './VersionComparisonModal';
import GitHubPrModal from './GitHubPrModal';

const NODE_TYPES = { archNode: CustomNode };

interface Props {
  graph: GraphResponse;
  onGraphChanged: (graphId: string) => void;
}

// Map component type → x/y bucket for auto-layout
const TYPE_COLUMN: Record<string, number> = {
  WEB_APPLICATION: 0,
  SERVICE:         1,
  SQL_DATABASE:    2,
  NOSQL_DATABASE:  2,
  DATABASE:        2,
  CACHE:           3,
  MESSAGE_BROKER:  3,
};

function buildFlowNodes(apiNodes: ApiNode[]): Node[] {
  const colCounts: Record<number, number> = {};
  return apiNodes.map(n => {
    const col = TYPE_COLUMN[n.type] ?? 1;
    const row = colCounts[col] ?? 0;
    colCounts[col] = row + 1;
    return {
      id: n.id,
      type: 'archNode',
      position: { x: col * 280 + 60, y: row * 140 + 60 },
      data: { node: n },
    };
  });
}

function buildFlowEdges(apiEdges: ApiEdge[]): Edge[] {
  return apiEdges.map(e => ({
    id: e.id,
    source: e.sourceNodeId,
    target: e.targetNodeId,
    label: e.edgeType,
    type: 'smoothstep',
    animated: e.edgeType === 'CALLS',
    markerEnd: { type: MarkerType.ArrowClosed, color: '#6366f1' },
    style: { stroke: '#6366f1', strokeWidth: 1.5, opacity: 0.7 },
    labelStyle: { fill: '#94a3b8', fontSize: 10 },
    labelBgStyle: { fill: '#161a27', fillOpacity: 0.9 },
    data: { edge: e },
  }));
}

export default function ArchCanvas({ graph, onGraphChanged }: Props) {
  const [simulateFlow, setSimulateFlow] = useState(false);
  const [showSecurityAi, setShowSecurityAi] = useState(false);
  const [showVersions, setShowVersions] = useState(false);
  const [showPrModal, setShowPrModal] = useState(false);


  const initialNodes = useMemo(() => buildFlowNodes(graph.nodes), [graph]);
  const initialEdges = useMemo(() => {
    return graph.edges.map(e => ({
      id: e.id,
      source: e.sourceNodeId,
      target: e.targetNodeId,
      label: e.edgeType,
      type: 'smoothstep',
      animated: simulateFlow || e.edgeType === 'CALLS',
      markerEnd: { type: MarkerType.ArrowClosed, color: simulateFlow ? '#10b981' : '#6366f1' },
      style: {
        stroke: simulateFlow ? '#10b981' : '#6366f1',
        strokeWidth: simulateFlow ? 2.5 : 1.5,
        opacity: simulateFlow ? 1 : 0.7,
        transition: 'all 0.3s ease',
      },
      labelStyle: { fill: simulateFlow ? '#6ee7b7' : '#94a3b8', fontSize: 10, fontWeight: simulateFlow ? 600 : 400 },
      labelBgStyle: { fill: '#161a27', fillOpacity: 0.9 },
      data: { edge: e },
    }));
  }, [graph, simulateFlow]);

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  // Sync edge animation whenever simulateFlow changes
  useEffect(() => {
    setEdges(initialEdges);
  }, [initialEdges, setEdges]);

  const [selectedNode, setSelectedNode] = useState<ApiNode | null>(null);
  const [showOverlay, setShowOverlay] = useState(false);


  const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    const apiNode = graph.nodes.find(n => n.id === node.id) ?? null;
    setSelectedNode(apiNode);
  }, [graph.nodes]);

  const onPaneClick = useCallback(() => setSelectedNode(null), []);

  return (
    <div style={{ width: '100%', height: '100%', position: 'relative' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        fitView
        fitViewOptions={{ padding: 0.15 }}
        minZoom={0.2}
        maxZoom={3}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={24} size={1} color="#1e2235" />
        <Controls position="bottom-left" />
        <MiniMap
          position="bottom-right"
          nodeColor={n => {
            const apiNode = graph.nodes.find(x => x.id === n.id);
            const t = apiNode?.type ?? '';
            if (t === 'SERVICE')        return '#6366f1';
            if (t.includes('DATABASE')) return '#10b981';
            if (t === 'CACHE')          return '#f59e0b';
            if (t === 'MESSAGE_BROKER') return '#ec4899';
            if (t === 'WEB_APPLICATION')return '#06b6d4';
            return '#475569';
          }}
          maskColor="rgba(13,15,23,0.7)"
        />
      </ReactFlow>

      {/* Toolbar */}
      <div style={{
        position: 'absolute', top: 16, left: 16, zIndex: 10,
        display: 'flex', gap: 8,
      }}>
        <button
          id="overlay-table-btn"
          className="btn-ghost"
          onClick={() => setShowOverlay(true)}
          style={{ fontSize: 12 }}
        >
          ✏️ Edit Graph
        </button>
        <button
          id="flow-toggle-btn"
          className={simulateFlow ? 'btn-primary' : 'btn-ghost'}
          onClick={() => setSimulateFlow(v => !v)}
          style={{ fontSize: 12 }}
        >
          ⚡ {simulateFlow ? 'Flow Active' : 'Simulate Request Flow'}
        </button>
        <button
          id="security-ai-btn"
          className="btn-ghost"
          onClick={() => setShowSecurityAi(true)}
          style={{ fontSize: 12 }}
        >
          🛡️ Security & AI
        </button>
        <button
          id="versions-btn"
          className="btn-ghost"
          onClick={() => setShowVersions(true)}
          style={{ fontSize: 12 }}
        >
          🔀 Versions
        </button>
        <button
          id="pr-events-btn"
          className="btn-ghost"
          onClick={() => setShowPrModal(true)}
          style={{ fontSize: 12 }}
        >
          🐙 PR Events
        </button>
      </div>

      {/* Graph stats */}
      <div style={{
        position: 'absolute', top: 16, right: selectedNode ? 380 : 16, zIndex: 10,
        display: 'flex', gap: 8, transition: 'right 0.25s ease',
      }}>
        <div style={{ padding: '6px 12px', background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12, color: 'var(--text-secondary)' }}>
          {graph.nodes.length} nodes · {graph.edges.length} edges
        </div>
        <div style={{ padding: '6px 12px', background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12, color: 'var(--text-muted)' }}>
          {graph.commitSha.slice(0, 8)}
        </div>
      </div>

      {/* Node Inspector panel */}
      {selectedNode && (
        <NodeInspector
          node={selectedNode}
          graphId={graph.id}
          onClose={() => setSelectedNode(null)}
          onUpdated={() => onGraphChanged(graph.id)}
        />
      )}

      {/* Overlay editor */}
      {showOverlay && (
        <OverlayTable
          graph={graph}
          onClose={() => setShowOverlay(false)}
          onChanged={() => { setShowOverlay(false); onGraphChanged(graph.id); }}
        />
      )}

      {/* Security & AI Advisory modal */}
      {showSecurityAi && (
        <SecurityAndAiModal
          graphId={graph.id}
          onClose={() => setShowSecurityAi(false)}
        />
      )}

      {/* Version Comparison modal */}
      {showVersions && (
        <VersionComparisonModal
          graphId={graph.id}
          onClose={() => setShowVersions(false)}
        />
      )}

      {/* GitHub PR Integration modal */}
      {showPrModal && (
        <GitHubPrModal
          repositoryId={graph.repositoryId}
          onClose={() => setShowPrModal(false)}
        />
      )}
    </div>
  );
}
