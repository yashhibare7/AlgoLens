import clsx from 'clsx';
import type { TraceEvent } from '../../types';
import { ACTION_CLASSES, ACTION_LABELS } from '../../lib/format';

/**
 * The sentence for the current step.
 *
 * <p>Written by the interpreter at the moment the statement ran, so it names real values
 * ("arr[0]=5 > arr[1]=2 -> true") rather than being reconstructed here from state the frontend
 * would have to interpret.
 */
export default function StepDetails({ step }: { step: TraceEvent | null }) {
  if (!step) {
    return (
      <p className="px-4 py-3 text-xs text-slate-500">
        Press Run to generate a trace, then step through it.
      </p>
    );
  }

  return (
    <div className="space-y-2 px-4 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={clsx(
            'rounded px-2 py-0.5 text-[11px] font-bold uppercase tracking-wide',
            ACTION_CLASSES[step.action],
          )}
        >
          {ACTION_LABELS[step.action]}
        </span>
        <span className="font-mono text-[11px] text-slate-500">line {step.line}</span>
        {step.depth > 0 && (
          <span className="chip">depth {step.depth}</span>
        )}
      </div>

      <p className="font-mono text-sm leading-relaxed text-slate-200">{step.message}</p>
    </div>
  );
}
