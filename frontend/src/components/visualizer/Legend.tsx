import clsx from 'clsx';
import type { ElementState } from '../../types';
import { CELL_CLASSES } from '../../lib/format';

/** What the cell colours mean. Without this the highlighting is decoration, not information. */
const ENTRIES: Array<{ state: ElementState; label: string }> = [
  { state: 'ACTIVE', label: 'read' },
  { state: 'COMPARING', label: 'compared' },
  { state: 'WRITTEN', label: 'written' },
  { state: 'SWAPPING', label: 'swapped' },
];

export default function Legend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-slate-800 pt-3">
      {ENTRIES.map(({ state, label }) => (
        <span key={state} className="flex items-center gap-1.5 text-[11px] text-slate-400">
          <span className={clsx('h-3 w-3 rounded border', CELL_CLASSES[state])} />
          {label}
        </span>
      ))}
    </div>
  );
}
