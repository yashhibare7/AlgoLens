import clsx from 'clsx';
import type { VisualizationState } from '../../types';
import { CELL_CLASSES, formatValue } from '../../lib/format';

/**
 * A 2D array as a grid.
 *
 * <p>Cell highlighting is not wired up for matrices yet: a touch identifies a cell by a single
 * index, which is unambiguous for a 1D array but not for a row/column pair. Rather than guess a
 * flattening the executor never promised, the grid renders uncoloured -- correct and plain
 * instead of colourful and wrong. Extending {@code Touch} with an optional row is the fix, and
 * it is on the roadmap with the tree and graph visualizers.
 */
export default function MatrixVisualizer({
  visualization,
}: {
  visualization: VisualizationState;
}) {
  const rows = visualization.rows ?? [];

  return (
    <div className="space-y-1">
      <div className="flex items-baseline gap-2">
        <span className="font-mono text-sm font-semibold text-slate-200">
          {visualization.name}
        </span>
        <span className="font-mono text-[11px] text-slate-500">
          {visualization.elementType}[{rows.length}][{rows[0]?.length ?? 0}]
        </span>
      </div>

      <div className="overflow-auto">
        <div className="inline-flex flex-col gap-1.5">
          {rows.map((row, rowIndex) => (
            <div key={rowIndex} className="flex items-center gap-1.5">
              <span className="w-5 text-right font-mono text-[10px] text-slate-500">
                {rowIndex}
              </span>
              {row.map((cell) => (
                <div
                  key={cell.index}
                  className={clsx(
                    'flex h-10 w-10 items-center justify-center rounded border-2 font-mono text-xs',
                    CELL_CLASSES[cell.state],
                  )}
                >
                  {formatValue(cell.value)}
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
