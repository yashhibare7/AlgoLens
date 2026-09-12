import clsx from 'clsx';
import type { Problem, SubmissionResponse, TestCaseOutcome } from '../../types';
import { VERDICT_CLASSES, VERDICT_LABELS } from '../../lib/format';

/**
 * The "solve" side of a judge-enabled problem: the signature to implement, the sample test cases
 * (visible before you ever submit), and -- once you do -- the verdict and per-test breakdown.
 *
 * <p>Hidden test cases only ever show pass/fail, never their input or expected output, the same
 * way a real judge withholds them from a failing submission.
 */
export default function JudgePanel({
  problem,
  submission,
  submitting,
  onSubmit,
}: {
  problem: Problem | null;
  submission: SubmissionResponse | null;
  submitting: boolean;
  onSubmit: () => void;
}) {
  if (!problem?.judgeEnabled) {
    return (
      <p className="px-4 py-3 text-xs text-slate-500">
        This problem does not have automated judging yet. Press Run to watch it execute
        step by step instead.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3 px-4 py-3 text-xs">
      <div>
        <div className="mb-1 font-semibold text-slate-300">Implement</div>
        <code className="block rounded-md bg-slate-900 px-2.5 py-1.5 font-mono text-[11px]
          text-sky-200">
          {problem.functionSignature}
        </code>
      </div>

      <div className="flex items-center gap-2">
        <button
          type="button"
          className="btn-primary !py-1.5 text-[11px]"
          onClick={onSubmit}
          disabled={submitting}
        >
          {submitting ? 'Judging...' : '✓ Submit'}
        </button>
        {submission && (
          <span className="text-slate-400">
            {submission.result.passedCount}/{submission.result.totalCount} test cases passed
          </span>
        )}
      </div>

      {submission && (
        <div
          className={clsx(
            'rounded-lg border px-3 py-2',
            VERDICT_CLASSES[submission.result.verdict],
          )}
        >
          <div className="font-semibold">{VERDICT_LABELS[submission.result.verdict]}</div>
          {submission.result.errorMessage && (
            <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap font-mono
              text-[11px] leading-relaxed">
              {submission.result.errorMessage}
            </pre>
          )}
        </div>
      )}

      <div className="space-y-1.5">
        <div className="font-semibold text-slate-300">Sample test cases</div>
        {problem.sampleTestCases.map((sample, i) => {
          const outcome = submission?.result.outcomes.find((o) => o.index === i && o.sample);
          return (
            <div key={i} className="rounded-md border border-slate-800 bg-slate-900/60 px-2.5 py-2">
              <div className="flex items-center justify-between gap-2">
                <span className="font-mono text-[11px] text-slate-300">
                  ({sample.arguments.join(', ')})
                </span>
                {outcome && <OutcomeBadge outcome={outcome} />}
              </div>
              <div className="mt-1 text-slate-400">
                expected <span className="font-mono text-slate-200">{sample.expectedOutput}</span>
                {outcome && !outcome.passed && outcome.actualOutput != null && (
                  <>
                    {' '}
                    got <span className="font-mono text-red-300">{outcome.actualOutput}</span>
                  </>
                )}
              </div>
              {sample.explanation && <div className="mt-1 text-slate-500">{sample.explanation}</div>}
            </div>
          );
        })}
      </div>

      {submission && submission.result.outcomes.some((o) => !o.sample) && (
        <div className="space-y-1">
          <div className="font-semibold text-slate-300">Hidden test cases</div>
          <div className="flex flex-wrap gap-1.5">
            {submission.result.outcomes
              .filter((o) => !o.sample)
              .map((outcome) => (
                <span
                  key={outcome.index}
                  className={clsx(
                    'rounded border px-1.5 py-0.5 font-mono text-[10px]',
                    outcome.passed
                      ? 'border-emerald-800 bg-emerald-950/50 text-emerald-300'
                      : 'border-red-900 bg-red-950/50 text-red-300',
                  )}
                >
                  #{outcome.index + 1} {outcome.passed ? 'pass' : 'fail'}
                </span>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}

function OutcomeBadge({ outcome }: { outcome: TestCaseOutcome }) {
  return (
    <span
      className={clsx(
        'shrink-0 rounded border px-1.5 py-0.5 text-[10px] font-semibold',
        outcome.passed
          ? 'border-emerald-800 bg-emerald-950/50 text-emerald-300'
          : 'border-red-900 bg-red-950/50 text-red-300',
      )}
    >
      {outcome.passed ? 'pass' : outcome.errorMessage ? 'error' : 'fail'}
    </span>
  );
}
