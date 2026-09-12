import clsx from 'clsx';
import { useMemo } from 'react';
import type { Player } from '../../hooks/usePlayer';
import type { ExecutionTrace, TraceAction } from '../../types';
import { ACTION_TICK_CLASSES } from '../../lib/format';

/** Actions worth jumping between, in the order they are offered. */
const JUMPABLE: Array<{ action: TraceAction; label: string }> = [
  { action: 'SWAP', label: 'swap' },
  { action: 'ARRAY_WRITE', label: 'write' },
  { action: 'FIELD_WRITE', label: 'field set' },
  { action: 'COMPARE', label: 'compare' },
  { action: 'ALLOCATE', label: 'new object' },
  { action: 'CALL', label: 'call' },
  { action: 'OUTPUT', label: 'print' },
  { action: 'ERROR', label: 'error' },
];

/**
 * Jump straight to the next occurrence of a kind of step.
 *
 * <p>Stepping is fine for twenty steps and useless for three hundred. The interesting moments in
 * a run are sparse — a bubble sort's swaps, a BFS's writes — so this skips to them directly.
 *
 * <p>Only kinds that actually occur in this trace get a button, and each shows how many there
 * are. That doubles as a summary of the run's shape before you navigate it at all.
 */
export default function JumpControls({
  trace,
  player,
}: {
  trace: ExecutionTrace;
  player: Player;
}) {
  const available = useMemo(() => {
    const counts = new Map<TraceAction, number>();
    for (const step of trace.steps) {
      counts.set(step.action, (counts.get(step.action) ?? 0) + 1);
    }
    return JUMPABLE.filter(({ action }) => counts.has(action)).map((entry) => ({
      ...entry,
      count: counts.get(entry.action) as number,
    }));
  }, [trace]);

  if (available.length === 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="text-[10px] uppercase tracking-wide text-slate-500">jump to</span>
      {available.map(({ action, label, count }) => (
        <span key={action} className="inline-flex items-stretch overflow-hidden rounded-md
          border border-slate-700">
          <button
            type="button"
            title={`Previous ${label}`}
            aria-label={`Previous ${label}`}
            className="px-1.5 text-[11px] text-slate-400 hover:bg-slate-700/60 hover:text-slate-100"
            onClick={() => player.jumpBackwardTo((step) => step.action === action)}
          >
            &#8249;
          </button>
          <button
            type="button"
            title={`Next ${label} (${count} in this run)`}
            className="flex items-center gap-1.5 bg-slate-800/60 px-2 py-1 text-[11px]
              text-slate-200 hover:bg-slate-700/60"
            onClick={() => player.jumpForwardTo((step) => step.action === action)}
          >
            <span className={clsx('h-2 w-2 rounded-sm', ACTION_TICK_CLASSES[action])} />
            {label}
            <span className="font-mono text-slate-500">{count}</span>
          </button>
        </span>
      ))}
    </div>
  );
}
