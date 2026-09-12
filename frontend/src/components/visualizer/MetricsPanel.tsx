import type { TraceMetrics } from '../../types';
import { formatDuration } from '../../lib/format';

/**
 * Run counters.
 *
 * <p>These are the numbers that make a complexity claim concrete. "O(n^2)" is a label; "15
 * element comparisons for 6 items, 45 for 10" is evidence, and re-running with a longer array
 * turns the label into something the learner verified themselves.
 *
 * <p>Counters that stayed at zero are hidden: an array sort has no object allocations and a
 * linked-list walk has no array writes, so showing every counter every time would bury the two
 * or three that matter for the algorithm on screen.
 *
 * <p>Note that comparisons counts comparisons of <em>data</em> only -- an array cell or an
 * object reference on at least one side. Loop bounds like {@code i < arr.length} are conditions,
 * which is also what a textbook analysis means.
 */
export default function MetricsPanel({
  metrics,
  durationMs,
}: {
  metrics: TraceMetrics;
  durationMs: number;
}) {
  const optional: Array<[string, number]> = [
    ['Comparisons', metrics.comparisons],
    ['Swaps', metrics.swaps],
    ['Array reads', metrics.arrayReads],
    ['Array writes', metrics.arrayWrites],
    ['Field reads', metrics.fieldReads],
    ['Field writes', metrics.fieldWrites],
    ['Objects created', metrics.objectsCreated],
    ['Method calls', metrics.calls],
    ['Max depth', metrics.maxCallDepth],
  ];

  const entries: Array<[string, string | number]> = [
    ['Steps', metrics.statements],
    ...optional.filter(([, value]) => value > 0),
    ['Time', formatDuration(durationMs)],
  ];

  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-2 px-4 py-3 sm:grid-cols-4">
      {entries.map(([label, value]) => (
        <div key={label}>
          <dt className="text-[10px] uppercase tracking-wide text-slate-500">{label}</dt>
          <dd className="font-mono text-sm font-semibold text-slate-100">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
