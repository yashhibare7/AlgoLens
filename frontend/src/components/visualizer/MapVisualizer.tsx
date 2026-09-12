import clsx from 'clsx';
import { motion } from 'framer-motion';
import type { VisualizationState } from '../../types';
import { CELL_CLASSES, formatValue } from '../../lib/format';

/**
 * A map, drawn as key/value rows.
 *
 * <p>Rows rather than a row of boxes, because a map's interesting operation is a lookup: the
 * question on screen is always "what is stored under this key", and a two-column layout answers
 * it directly. The touched row is highlighted exactly like an array cell, so
 * `counts.get("a")` reads the same way `arr[0]` does.
 */
export default function MapVisualizer({
  visualization,
}: {
  visualization: VisualizationState;
}) {
  const entries = visualization.entries ?? [];

  return (
    <div className="space-y-1">
      <div className="flex items-baseline gap-2">
        <span className="font-mono text-sm font-semibold text-slate-200">
          {visualization.name}
        </span>
        <span className="font-mono text-[11px] text-slate-500">
          {visualization.elementType} &middot; {entries.length}{' '}
          {entries.length === 1 ? 'entry' : 'entries'}
        </span>
      </div>

      {entries.length === 0 ? (
        <p className="font-mono text-xs text-slate-500">&#123;&#125; empty</p>
      ) : (
        <div className="max-h-64 overflow-auto">
          <table className="w-full max-w-md border-separate border-spacing-y-1">
            <thead>
              <tr className="text-[10px] uppercase tracking-wide text-slate-500">
                <th className="w-1/2 px-2 text-left font-medium">key</th>
                <th className="w-1/2 px-2 text-left font-medium">value</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <motion.tr
                  key={String(entry.key)}
                  layout
                  initial={{ opacity: 0, x: -6 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ duration: 0.16 }}
                >
                  <td
                    className={clsx(
                      'rounded-l-lg border-y-2 border-l-2 px-2 py-1.5 font-mono text-xs',
                      CELL_CLASSES[entry.state],
                    )}
                  >
                    {formatValue(entry.key)}
                  </td>
                  <td
                    className={clsx(
                      'rounded-r-lg border-y-2 border-r-2 px-2 py-1.5 font-mono text-xs font-semibold',
                      CELL_CLASSES[entry.state],
                    )}
                  >
                    {formatValue(entry.value)}
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
