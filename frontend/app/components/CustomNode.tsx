'use client';

// CustomNode — M1.12
// The visual node card rendered inside React Flow.

import { Handle, Position } from '@xyflow/react';
import { GraphNode } from '../lib/api';

interface Props {
  data: { node: GraphNode };
  selected?: boolean;
}

const TYPE_CONFIG: Record<string, { icon: string; color: string; label: string }> = {
  SERVICE:         { icon: '⚙️',  color: '#6366f1', label: 'Service'   },
  WEB_APPLICATION: { icon: '🌐',  color: '#06b6d4', label: 'Web App'   },
  SQL_DATABASE:    { icon: '🗄️',  color: '#10b981', label: 'SQL DB'    },
  NOSQL_DATABASE:  { icon: '📦',  color: '#10b981', label: 'NoSQL DB'  },
  DATABASE:        { icon: '🗄️',  color: '#10b981', label: 'Database'  },
  CACHE:           { icon: '⚡',  color: '#f59e0b', label: 'Cache'     },
  MESSAGE_BROKER:  { icon: '📨',  color: '#ec4899', label: 'Broker'    },
};

export default function CustomNode({ data, selected }: Props) {
  const { node } = data;
  const cfg = TYPE_CONFIG[node.type] ?? { icon: '📌', color: '#475569', label: node.type };
  const confidence = Math.round(node.confidence * 100);

  return (
    <>
      <Handle type="target" position={Position.Left} style={{ background: cfg.color, border: 'none', width: 8, height: 8 }} />

      <div
        id={`node-${node.id}`}
        style={{
          minWidth: 180, maxWidth: 220,
          background: 'var(--bg-card)',
          border: `1.5px solid ${selected ? cfg.color : 'var(--border)'}`,
          borderRadius: 12,
          padding: '12px 14px',
          boxShadow: selected ? `0 0 0 2px ${cfg.color}33, 0 8px 32px rgba(0,0,0,0.5)` : '0 4px 16px rgba(0,0,0,0.4)',
          transition: 'border-color 0.2s, box-shadow 0.2s',
          cursor: 'pointer',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <div style={{
            width: 32, height: 32, borderRadius: 8,
            background: `${cfg.color}22`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 16, flexShrink: 0,
          }}>
            {cfg.icon}
          </div>
          <div style={{ minWidth: 0 }}>
            <p style={{ margin: 0, fontWeight: 700, fontSize: 13, color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {node.name}
            </p>
            <p style={{ margin: 0, fontSize: 10, color: cfg.color, textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
              {cfg.label}
            </p>
          </div>
        </div>

        {/* Technology */}
        {node.technology && (
          <p style={{ margin: '0 0 6px', fontSize: 11, color: 'var(--text-secondary)', background: 'var(--bg-secondary)', padding: '3px 7px', borderRadius: 4, display: 'inline-block' }}>
            {node.technology}
          </p>
        )}

        {/* Confidence bar */}
        <div style={{ marginTop: 6 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
            <span style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Confidence</span>
            <span style={{ fontSize: 9, color: cfg.color, fontWeight: 700 }}>{confidence}%</span>
          </div>
          <div style={{ height: 3, background: 'var(--border)', borderRadius: 99 }}>
            <div style={{ height: '100%', width: `${confidence}%`, background: cfg.color, borderRadius: 99 }} />
          </div>
        </div>
      </div>

      <Handle type="source" position={Position.Right} style={{ background: cfg.color, border: 'none', width: 8, height: 8 }} />
    </>
  );
}
