import clsx from 'clsx';
import type { VariableValue } from '../../types';
import { formatValue } from '../../lib/format';

/**
 * Scalar variables at the current step.
 *
 * <p>Rows the step changed are marked, so the answer to "what just happened" is visible without
 * diffing two screenshots. The flag comes from the interpreter, which knows exactly which names
 * it wrote.
 */
export default function VariablePanel({ variables }: { variables: VariableValue[] }) {
  if (variables.length === 0) {
    return <p className="px-4 py-3 text-xs text-slate-500">No variables in scope yet.</p>;
  }

  return (
    <div className="divide-y divide-slate-800/70">
      {variables.map((variable) => (
        <div
          key={variable.name}
          className={clsx(
            'flex items-baseline gap-2 px-4 py-1.5 font-mono text-xs',
            variable.changed && 'bg-sky-500/10',
          )}
        >
          <span className="w-14 shrink-0 text-[10px] uppercase tracking-wide text-slate-500">
            {variable.type}
          </span>
          <span className="min-w-0 flex-1 truncate text-slate-300">{variable.name}</span>
          <span
            className={clsx(
              'shrink-0 font-semibold',
              variable.changed ? 'text-sky-300' : 'text-slate-100',
            )}
          >
            {formatValue(variable.value)}
          </span>
        </div>
      ))}
    </div>
  );
}
