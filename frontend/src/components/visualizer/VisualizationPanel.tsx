import type { TraceEvent } from '../../types';
import ArrayVisualizer from './ArrayVisualizer';
import MatrixVisualizer from './MatrixVisualizer';
import MapVisualizer from './MapVisualizer';
import ObjectGraphVisualizer from './ObjectGraphVisualizer';
import Legend from './Legend';

/**
 * Dispatches each drawable structure in the current step to its renderer.
 *
 * <p>The switch is the only place the frontend cares what kind of structure it is looking at.
 * When the backend gains a linked-list or tree executor, this file grows one case and nothing
 * else in the app changes -- which is the payoff of the language-independent trace contract.
 */
export default function VisualizationPanel({ step }: { step: TraceEvent | null }) {
  const visualizations = step?.visualizations ?? [];

  if (visualizations.length === 0) {
    return (
      <div className="flex h-full min-h-[220px] flex-col items-center justify-center gap-2 px-6 text-center">
        <p className="text-sm text-slate-400">No data structure in scope at this step.</p>
        <p className="max-w-sm text-xs text-slate-600">
          Declare an array -- <code className="font-mono">int[] arr = &#123;5, 2, 8&#125;;</code>{' '}
          -- or build objects with a <code className="font-mono">next</code> or{' '}
          <code className="font-mono">left</code>/<code className="font-mono">right</code> field,
          and they will be drawn here as the code runs.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-5 p-4">
      {visualizations.map((visualization, index) => {
        // Two structures can share a name in principle, so the index keeps keys unique.
        const key = `${visualization.type}-${visualization.name}-${index}`;
        switch (visualization.type) {
          case 'ARRAY':
          case 'STACK':
          case 'QUEUE':
          case 'SET':
            return <ArrayVisualizer key={key} visualization={visualization} />;
          case 'MAP':
            return <MapVisualizer key={key} visualization={visualization} />;
          case 'MATRIX':
            return <MatrixVisualizer key={key} visualization={visualization} />;
          case 'LINKED_LIST':
          case 'TREE':
          case 'GRAPH':
            return <ObjectGraphVisualizer key={key} visualization={visualization} />;
          default:
            // Every VisualizationType is handled above, so this branch is unreachable and
            // `visualization.type` narrows to `never`. Adding a type to the backend contract
            // without adding a renderer here becomes a compile error rather than a blank panel.
            return assertNeverRendered(visualization.type, key);
        }
      })}
      <Legend />
    </div>
  );
}

/**
 * Renders a visible placeholder for a structure type the frontend does not know about.
 *
 * <p>Typed to take `never`, so the compiler enforces that the switch above stays exhaustive;
 * the runtime body only matters if a backend sends a type this build predates.
 */
function assertNeverRendered(type: never, key: string) {
  return (
    <div
      key={key}
      className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-xs text-slate-400"
    >
      This backend sent a <span className="font-mono text-slate-200">{String(type)}</span>{' '}
      structure, which this version of the UI cannot draw yet.
    </div>
  );
}
