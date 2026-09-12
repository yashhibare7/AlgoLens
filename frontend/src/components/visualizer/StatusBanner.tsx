import clsx from 'clsx';
import type { ReactNode } from 'react';
import type { ExecutionTrace } from '../../types';
import { STATUS_CLASSES, STATUS_LABELS } from '../../lib/format';

/**
 * The outcome of a run, with the failing line when there is one.
 *
 * <p>Failure is the common case for a learner, so the banner is written to be useful rather than
 * alarming: it names the line, quotes the interpreter's message verbatim (including multi-line
 * messages, which the backend uses to hand back a ready-to-paste fix), and stays out of the way
 * on success by summarising the run instead.
 */
export default function StatusBanner({
  trace,
  onJumpToLine,
  action,
}: {
  trace: ExecutionTrace;
  onJumpToLine?: (line: number) => void;
  /** An offered fix, rendered next to the message. */
  action?: ReactNode;
}) {
  const failed = trace.status !== 'SUCCESS';
  const message = trace.errorMessage;
  // The backend sends a code snippet in some messages; preserve its indentation.
  const multiline = Boolean(message && message.includes('\n'));

  return (
    <div
      className={clsx(
        'rounded-lg border px-3 py-2 text-xs',
        STATUS_CLASSES[trace.status],
      )}
      role={failed ? 'alert' : undefined}
    >
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="font-semibold">{STATUS_LABELS[trace.status]}</span>

        {message && !multiline && <span className="font-mono">{message}</span>}

        {trace.errorLine != null && onJumpToLine && (
          <button
            type="button"
            className="underline decoration-dotted underline-offset-2 hover:no-underline"
            onClick={() => onJumpToLine(trace.errorLine as number)}
          >
            go to line {trace.errorLine}
          </button>
        )}

        {!failed && (
          <span className="text-slate-400">
            {trace.totalSteps} steps in {trace.durationMs} ms
          </span>
        )}

        {trace.truncated && (
          <span className="font-semibold text-orange-300">
            Trace truncated -- the run was longer than the step budget. Try a smaller input.
          </span>
        )}

        {action}
      </div>

      {message && multiline && (
        <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap font-mono text-[11px]
          leading-relaxed">
          {message}
        </pre>
      )}
    </div>
  );
}
