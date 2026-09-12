import { motion } from 'framer-motion';
import clsx from 'clsx';
import type { VisualizationState } from '../../types';
import { CELL_CLASSES, formatValue } from '../../lib/format';

interface Props {
  visualization: VisualizationState;
}

/**
 * One array, drawn as indexed cells with pointer arrows underneath.
 *
 * <p>Cell colour comes from {@code ElementState}, which the backend computed from the step's
 * touches -- so "which cells is this step looking at" is answered by the executor, not guessed
 * here. That is what keeps the picture honest when a new language is added.
 *
 * <p>Each cell is keyed by index and animated on value change, so a write reads as a value
 * landing in a slot rather than the whole row repainting.
 */
export default function ArrayVisualizer({ visualization }: Props) {
  const elements = visualization.elements ?? [];
  const pointers = visualization.pointers ?? [];

  // A set has no positions, so showing index labels above it would invent an ordering the
  // structure does not actually promise.
  const showIndices = visualization.type !== 'SET';

  // Wide arrays get narrower cells rather than a scrollbar, up to a point.
  const compact = elements.length > 16;
  const cellSize = compact ? 'h-11 w-11 text-xs' : 'h-14 w-14 text-base';

  const pointersByIndex = new Map<number, string[]>();
  for (const pointer of pointers) {
    // A pointer without an index addresses an object, not a cell -- ignore it here.
    if (pointer.index == null) {
      continue;
    }
    const names = pointersByIndex.get(pointer.index) ?? [];
    names.push(pointer.name);
    pointersByIndex.set(pointer.index, names);
  }

  return (
    <div className="space-y-1">
      <div className="flex items-baseline gap-2">
        <span className="font-mono text-sm font-semibold text-slate-200">
          {visualization.name}
        </span>
        {visualization.elementType && (
          <span className="font-mono text-[11px] text-slate-500">
            {showIndices
              ? `${visualization.elementType}[${elements.length}]`
              : `${visualization.elementType} · ${elements.length}`}
          </span>
        )}
      </div>

      <div className="overflow-x-auto pb-1">
        <div className="flex min-w-fit gap-1.5">
          {elements.map((element) => {
            const names = pointersByIndex.get(element.index);
            return (
              <div key={element.index} className="flex flex-col items-center gap-1">
                <span className="font-mono text-[10px] text-slate-500">
                  {showIndices ? element.index : ''}
                </span>

                <motion.div
                  layout
                  className={clsx(
                    'flex items-center justify-center rounded-lg border-2 font-mono font-semibold',
                    cellSize,
                    CELL_CLASSES[element.state],
                  )}
                  animate={{ scale: element.state === 'DEFAULT' ? 1 : 1.06 }}
                  transition={{ type: 'spring', stiffness: 480, damping: 26 }}
                >
                  {/* Re-keyed on value so a changed cell animates in instead of mutating
                      silently -- the movement is what the eye follows. */}
                  <motion.span
                    key={formatValue(element.value)}
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.16 }}
                  >
                    {formatValue(element.value)}
                  </motion.span>
                </motion.div>

                <div className="flex h-8 flex-col items-center">
                  {names && (
                    <>
                      <span className="text-sky-400" aria-hidden>
                        &#9650;
                      </span>
                      <span className="font-mono text-[10px] font-semibold text-sky-300">
                        {names.join(',')}
                      </span>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
