import clsx from 'clsx';
import type { StackFrameState } from '../../types';
import { formatValue } from '../../lib/format';

/**
 * The call stack, innermost frame highlighted.
 *
 * <p>The backend omits the stack entirely while only the entry frame is active, so this panel
 * simply does not render for straight-line array code. It appears the moment a method is called,
 * which makes it a much better teaching signal than an always-visible panel showing one frame.
 */
export default function CallStackPanel({ frames }: { frames: StackFrameState[] }) {
  if (frames.length === 0) {
    return null;
  }

  return (
    <div className="space-y-1.5 p-3">
      {/* Innermost last in the payload; reversed here so the active frame is on top, the way
          every debugger presents it. */}
      {[...frames].reverse().map((frame, position) => (
        <div
          key={`${frame.method}-${frames.length - position}`}
          className={clsx(
            'rounded-lg border px-3 py-2',
            position === 0
              ? 'border-indigo-700 bg-indigo-950/40'
              : 'border-slate-800 bg-slate-900/60',
          )}
        >
          <div className="flex items-baseline justify-between gap-2">
            <span className="font-mono text-xs font-semibold text-slate-100">
              {frame.method}()
            </span>
            <span className="font-mono text-[10px] text-slate-500">line {frame.line}</span>
          </div>
          {frame.variables.length > 0 && (
            <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5">
              {frame.variables.map((variable) => (
                <span key={variable.name} className="font-mono text-[11px] text-slate-400">
                  {variable.name}=
                  <span className="text-slate-200">{formatValue(variable.value)}</span>
                </span>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
