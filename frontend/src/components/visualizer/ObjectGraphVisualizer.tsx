import { useMemo } from 'react';
import type { GraphEdge, GraphNode, VisualizationState } from '../../types';
import { formatValue, NODE_COLORS } from '../../lib/format';

const NODE_WIDTH = 84;
const NODE_HEIGHT = 50;
/** Horizontal centre-to-centre distance: node width plus room for an arrow. */
const STEP_X = NODE_WIDTH + 46;
const STEP_Y = NODE_HEIGHT + 46;
/** Headroom above the first row for pointer labels. */
const TOP_PAD = 30;
const SIDE_PAD = 16;
/** Wrap a long list rather than producing one enormous horizontal scroll. */
const MAX_PER_ROW = 6;

interface Point {
  x: number;
  y: number;
}

interface Layout {
  positions: Map<string, Point>;
  width: number;
  height: number;
}

/**
 * Draws every reference-linked structure: linked lists, trees and general object graphs.
 *
 * <p>One component rather than three, because the payload is identical -- nodes plus labelled
 * edges -- and only the layout differs. The backend picks the layout from the *names* of the
 * reference fields (`next` means a list, `left`/`right` means a tree), which is deliberately
 * more stable than inferring it from runtime shape: a tree whose right subtree is temporarily
 * empty is structurally a list, and reclassifying it mid-run would make the picture jump around
 * as nodes are inserted.
 *
 * <p>Node colours come from the backend's `state`, computed from the step's touches, so a node
 * being read or compared lights up for the same reason an array cell does.
 */
export default function ObjectGraphVisualizer({
  visualization,
}: {
  visualization: VisualizationState;
}) {
  const nodes = visualization.nodes ?? [];
  const edges = visualization.edges ?? [];

  const layout = useMemo(
    () => computeLayout(visualization.type, nodes, edges),
    [visualization.type, nodes, edges],
  );

  if (nodes.length === 0) {
    return null;
  }

  const byId = new Map(nodes.map((node) => [node.id, node]));
  const pointersByNode = new Map<string, string[]>();
  for (const pointer of visualization.pointers ?? []) {
    if (!pointer.nodeId) {
      continue;
    }
    const names = pointersByNode.get(pointer.nodeId) ?? [];
    names.push(pointer.name);
    pointersByNode.set(pointer.nodeId, names);
  }

  const shapeLabel =
    visualization.type === 'LINKED_LIST'
      ? 'linked list'
      : visualization.type === 'TREE'
        ? 'tree'
        : 'object graph';

  return (
    <div className="space-y-1">
      <div className="flex items-baseline gap-2">
        <span className="font-mono text-sm font-semibold text-slate-200">
          {visualization.name}
        </span>
        <span className="font-mono text-[11px] text-slate-500">
          {shapeLabel} &middot; {nodes.length} node{nodes.length === 1 ? '' : 's'}
        </span>
      </div>

      <div className="overflow-x-auto pb-1">
        <svg
          viewBox={`0 0 ${layout.width} ${layout.height}`}
          width={layout.width}
          height={layout.height}
          className="max-w-full"
          role="img"
          aria-label={`${shapeLabel} with ${nodes.length} nodes`}
        >
          <defs>
            <marker
              id="algolens-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#64748b" />
            </marker>
          </defs>

          {/* Edges first, so nodes paint over the line ends. */}
          {edges.map((edge, index) => (
            <Edge
              key={`${edge.from}-${edge.label}-${index}`}
              edge={edge}
              layout={layout}
              showNullTerminator={visualization.type === 'LINKED_LIST'}
              multiField={hasMultipleFields(edges)}
            />
          ))}

          {nodes.map((node) => {
            const position = layout.positions.get(node.id);
            if (!position) {
              return null;
            }
            return (
              <NodeBox
                key={node.id}
                node={node}
                position={position}
                pointers={pointersByNode.get(node.id) ?? []}
              />
            );
          })}
        </svg>
      </div>

      <FieldReadout nodes={nodes} pointers={pointersByNode} byId={byId} />
    </div>
  );
}

/** Edge labels are only worth the clutter when there is more than one kind of reference. */
function hasMultipleFields(edges: GraphEdge[]): boolean {
  const labels = new Set(edges.map((edge) => edge.label));
  return labels.size > 1;
}

// ---------------------------------------------------------------- pieces

function NodeBox({
  node,
  position,
  pointers,
}: {
  node: GraphNode;
  position: Point;
  pointers: string[];
}) {
  const colors = NODE_COLORS[node.state] ?? NODE_COLORS.DEFAULT;
  const payload = node.value === undefined || node.value === null ? '' : formatValue(node.value);

  return (
    <g>
      {pointers.length > 0 && (
        <text
          x={position.x + NODE_WIDTH / 2}
          y={position.y - 8}
          textAnchor="middle"
          className="font-mono"
          fontSize="10"
          fontWeight="700"
          fill="#38bdf8"
        >
          {pointers.join(', ')}
        </text>
      )}

      <rect
        x={position.x}
        y={position.y}
        width={NODE_WIDTH}
        height={NODE_HEIGHT}
        rx="8"
        fill={colors.fill}
        stroke={colors.stroke}
        strokeWidth="2"
      />

      <text
        x={position.x + NODE_WIDTH / 2}
        y={position.y + 16}
        textAnchor="middle"
        fontSize="9"
        fill="#94a3b8"
        className="font-mono"
      >
        {node.className}
      </text>

      <text
        x={position.x + NODE_WIDTH / 2}
        y={position.y + 36}
        textAnchor="middle"
        fontSize="15"
        fontWeight="700"
        fill={colors.text}
        className="font-mono"
      >
        {payload}
      </text>
    </g>
  );
}

function Edge({
  edge,
  layout,
  showNullTerminator,
  multiField,
}: {
  edge: GraphEdge;
  layout: Layout;
  showNullTerminator: boolean;
  multiField: boolean;
}) {
  const from = layout.positions.get(edge.from);
  if (!from) {
    return null;
  }

  // A null reference is the structure's terminator. Worth drawing for a list, where it marks
  // the tail; noise for a tree, where every leaf would sprout two of them.
  if (!edge.to) {
    if (!showNullTerminator) {
      return null;
    }
    const startX = from.x + NODE_WIDTH;
    const y = from.y + NODE_HEIGHT / 2;
    return (
      <g>
        <line
          x1={startX}
          y1={y}
          x2={startX + 26}
          y2={y}
          stroke="#475569"
          strokeWidth="1.5"
          strokeDasharray="3 3"
          markerEnd="url(#algolens-arrow)"
        />
        <text x={startX + 30} y={y + 4} fontSize="10" fill="#64748b" className="font-mono">
          null
        </text>
      </g>
    );
  }

  const to = layout.positions.get(edge.to);
  if (!to) {
    return null;
  }

  const sameRow = from.y === to.y;
  const adjacent = sameRow && Math.abs(to.x - from.x) === STEP_X;

  if (sameRow && !adjacent) {
    // A jump within a row: the cycle back-edge in a circular list. Arc it underneath so it
    // does not run straight through the nodes in between.
    const startX = from.x + NODE_WIDTH / 2;
    const endX = to.x + NODE_WIDTH / 2;
    const baseY = from.y + NODE_HEIGHT;
    const depth = Math.min(34, 14 + Math.abs(endX - startX) / 10);
    return (
      <g>
        <path
          d={`M ${startX} ${baseY} C ${startX} ${baseY + depth}, ${endX} ${baseY + depth}, ${endX} ${baseY}`}
          fill="none"
          stroke="#fb7185"
          strokeWidth="1.75"
          markerEnd="url(#algolens-arrow)"
        />
        {multiField && (
          <text
            x={(startX + endX) / 2}
            y={baseY + depth + 11}
            textAnchor="middle"
            fontSize="9"
            fill="#fb7185"
            className="font-mono"
          >
            {edge.label}
          </text>
        )}
      </g>
    );
  }

  // Straight: horizontal along a row, or diagonal from a parent down to a child.
  const start = sameRow
    ? { x: from.x + NODE_WIDTH, y: from.y + NODE_HEIGHT / 2 }
    : { x: from.x + NODE_WIDTH / 2, y: from.y + NODE_HEIGHT };
  const end = sameRow
    ? { x: to.x, y: to.y + NODE_HEIGHT / 2 }
    : { x: to.x + NODE_WIDTH / 2, y: to.y };

  return (
    <g>
      <line
        x1={start.x}
        y1={start.y}
        x2={end.x}
        y2={end.y}
        stroke="#64748b"
        strokeWidth="1.75"
        markerEnd="url(#algolens-arrow)"
      />
      {multiField && (
        <text
          x={(start.x + end.x) / 2 + (sameRow ? 0 : 10)}
          y={(start.y + end.y) / 2 - 4}
          textAnchor="middle"
          fontSize="9"
          fill="#94a3b8"
          className="font-mono"
        >
          {edge.label}
        </text>
      )}
    </g>
  );
}

/**
 * A text readout of the node a pointer currently sits on.
 *
 * <p>The boxes show one payload each; this is where the rest of the fields live, for the node
 * the algorithm is actually working with. Without it, `prev`/`data` on a doubly linked list
 * would be invisible.
 */
function FieldReadout({
  nodes,
  pointers,
  byId,
}: {
  nodes: GraphNode[];
  pointers: Map<string, string[]>;
  byId: Map<string, GraphNode>;
}) {
  const highlighted = nodes.filter((node) => node.state !== 'DEFAULT');
  const pointed = [...pointers.keys()].map((id) => byId.get(id)).filter(Boolean) as GraphNode[];
  const interesting = (highlighted.length > 0 ? highlighted : pointed).slice(0, 3);

  if (interesting.length === 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1 pt-1">
      {interesting.map((node) => (
        <span key={node.id} className="font-mono text-[11px] text-slate-400">
          <span className="text-slate-300">{node.id}</span>
          {'  '}
          {node.fields
            .map((field) => `${field.name}=${formatValue(field.value)}`)
            .join('  ')}
        </span>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- layout

function computeLayout(
  type: VisualizationState['type'],
  nodes: GraphNode[],
  edges: GraphEdge[],
): Layout {
  if (nodes.length === 0) {
    return { positions: new Map(), width: 1, height: 1 };
  }
  if (type === 'TREE') {
    return treeLayout(nodes, edges);
  }
  if (type === 'LINKED_LIST') {
    return chainLayout(nodes, edges);
  }
  return layeredLayout(nodes, edges);
}

/** Ids that nothing points at: the natural starting points of a structure. */
function rootsOf(nodes: GraphNode[], edges: GraphEdge[]): string[] {
  const targeted = new Set(edges.map((edge) => edge.to).filter(Boolean) as string[]);
  const roots = nodes.filter((node) => !targeted.has(node.id)).map((node) => node.id);
  // Every node has an incoming edge in a fully circular list; fall back to the first node so
  // the picture still renders instead of collapsing to nothing.
  return roots.length > 0 ? roots : [nodes[0].id];
}

function adjacencyOf(edges: GraphEdge[]): Map<string, string[]> {
  const adjacency = new Map<string, string[]>();
  for (const edge of edges) {
    if (!edge.to) {
      continue;
    }
    const targets = adjacency.get(edge.from) ?? [];
    targets.push(edge.to);
    adjacency.set(edge.from, targets);
  }
  return adjacency;
}

/** Left to right, wrapping into rows. Used for linked lists. */
function chainLayout(nodes: GraphNode[], edges: GraphEdge[]): Layout {
  const adjacency = adjacencyOf(edges);
  const order: string[] = [];
  const seen = new Set<string>();

  const walk = (start: string) => {
    let currentId: string | undefined = start;
    while (currentId && !seen.has(currentId)) {
      seen.add(currentId);
      order.push(currentId);
      currentId = (adjacency.get(currentId) ?? []).find((next) => !seen.has(next));
    }
  };

  rootsOf(nodes, edges).forEach(walk);
  // Anything not reached (a separate fragment inside the same component) still gets placed.
  nodes.forEach((node) => walk(node.id));

  const positions = new Map<string, Point>();
  order.forEach((id, index) => {
    const row = Math.floor(index / MAX_PER_ROW);
    const column = index % MAX_PER_ROW;
    positions.set(id, {
      x: SIDE_PAD + column * STEP_X,
      y: TOP_PAD + row * (STEP_Y + 20),
    });
  });

  const rows = Math.ceil(order.length / MAX_PER_ROW);
  const columns = Math.min(order.length, MAX_PER_ROW);
  return {
    positions,
    // Extra right margin leaves room for the trailing "null" terminator.
    width: SIDE_PAD * 2 + (columns - 1) * STEP_X + NODE_WIDTH + 60,
    height: TOP_PAD + rows * (STEP_Y + 20) + 24,
  };
}

/** Depth for y, in-order sequence for x. Keeps subtrees from overlapping. */
function treeLayout(nodes: GraphNode[], edges: GraphEdge[]): Layout {
  const children = new Map<string, { left?: string; right?: string; others: string[] }>();
  for (const edge of edges) {
    if (!edge.to) {
      continue;
    }
    const entry = children.get(edge.from) ?? { others: [] };
    if (edge.label === 'left') {
      entry.left = edge.to;
    } else if (edge.label === 'right') {
      entry.right = edge.to;
    } else if (edge.label !== 'parent') {
      // 'parent' back-references are skipped: they duplicate an edge already drawn downward.
      entry.others.push(edge.to);
    }
    children.set(edge.from, entry);
  }

  const positions = new Map<string, Point>();
  const visited = new Set<string>();
  let column = 0;
  let maxDepth = 0;

  const place = (id: string, depth: number) => {
    if (visited.has(id)) {
      return;
    }
    visited.add(id);
    maxDepth = Math.max(maxDepth, depth);
    const entry = children.get(id) ?? { others: [] };

    if (entry.left) {
      place(entry.left, depth + 1);
    }
    positions.set(id, {
      x: SIDE_PAD + column * STEP_X,
      y: TOP_PAD + depth * STEP_Y,
    });
    column++;
    if (entry.right) {
      place(entry.right, depth + 1);
    }
    entry.others.forEach((child) => place(child, depth + 1));
  };

  rootsOf(nodes, edges).forEach((root) => place(root, 0));
  nodes.forEach((node) => place(node.id, 0));

  return {
    positions,
    width: SIDE_PAD * 2 + Math.max(1, column) * STEP_X,
    height: TOP_PAD + (maxDepth + 1) * STEP_Y + 16,
  };
}

/** Breadth-first layers for anything that is neither a list nor a tree. */
function layeredLayout(nodes: GraphNode[], edges: GraphEdge[]): Layout {
  const adjacency = adjacencyOf(edges);
  const depths = new Map<string, number>();
  const queue: string[] = [];

  for (const root of rootsOf(nodes, edges)) {
    depths.set(root, 0);
    queue.push(root);
  }
  while (queue.length > 0) {
    const currentId = queue.shift() as string;
    const depth = depths.get(currentId) ?? 0;
    for (const next of adjacency.get(currentId) ?? []) {
      if (!depths.has(next)) {
        depths.set(next, depth + 1);
        queue.push(next);
      }
    }
  }
  // Nodes only reachable against the edge direction still need a home.
  nodes.forEach((node) => {
    if (!depths.has(node.id)) {
      depths.set(node.id, 0);
    }
  });

  const perDepth = new Map<number, number>();
  const positions = new Map<string, Point>();
  let widest = 0;
  let maxDepth = 0;

  for (const node of nodes) {
    const depth = depths.get(node.id) ?? 0;
    const column = perDepth.get(depth) ?? 0;
    perDepth.set(depth, column + 1);
    widest = Math.max(widest, column + 1);
    maxDepth = Math.max(maxDepth, depth);
    positions.set(node.id, {
      x: SIDE_PAD + column * STEP_X,
      y: TOP_PAD + depth * STEP_Y,
    });
  }

  return {
    positions,
    width: SIDE_PAD * 2 + Math.max(1, widest) * STEP_X,
    height: TOP_PAD + (maxDepth + 1) * STEP_Y + 16,
  };
}
