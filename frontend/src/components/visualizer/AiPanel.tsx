import clsx from 'clsx';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import { buildTraceExcerpt } from '../../lib/traceExcerpt';
import { useAuth } from '../../store/AuthContext';
import type { ExecutionTrace, ExplainMode, ExplainResponse } from '../../types';
import MiniMarkdown from '../MiniMarkdown';

interface Props {
  trace: ExecutionTrace | null;
  code: string;
  language: string;
  stepIndex: number;
}

const MODES: Array<{ mode: ExplainMode; label: string; hint: string }> = [
  { mode: 'EXPLAIN_STEP', label: 'Explain this step', hint: 'What is happening right here' },
  { mode: 'EXPLAIN_CODE', label: 'Explain the algorithm', hint: 'The strategy end to end' },
  { mode: 'COMPLEXITY', label: 'Complexity', hint: 'Time and space, justified' },
  { mode: 'FIND_BUG', label: 'Find my bug', hint: 'Reviewed against the trace' },
];

/**
 * The AI explanation panel.
 *
 * <p>Requests carry the code plus a rendered window of the real trace, so answers are grounded
 * in what this run actually did rather than in what the algorithm usually does. The API key
 * never reaches the browser: everything goes through the backend's own endpoint.
 */
export default function AiPanel({ trace, code, language, stepIndex }: Props) {
  const { user, meta, setCreditBalance } = useAuth();
  const [pending, setPending] = useState<ExplainMode | null>(null);
  const [result, setResult] = useState<ExplainResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [question, setQuestion] = useState('');

  const ask = async (mode: ExplainMode) => {
    if (!trace) {
      return;
    }
    setPending(mode);
    setError(null);
    try {
      const response = await api.ai.explain({
        mode,
        language,
        code,
        stepIndex: trace.steps[stepIndex]?.step,
        traceExcerpt: buildTraceExcerpt(trace, stepIndex),
        question: question.trim() || undefined,
      });
      setResult(response);
      if (response.creditBalance != null) {
        setCreditBalance(response.creditBalance);
      }
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.message : 'Could not get an explanation right now.',
      );
    } finally {
      setPending(null);
    }
  };

  if (!user) {
    return (
      <div className="space-y-2 px-4 py-4 text-sm text-slate-400">
        <p>AI explanations are tied to an account, because each one costs real money to run.</p>
        <Link to="/login" className="btn-ghost">
          Sign in to use them
        </Link>
      </div>
    );
  }

  const explanationCost = meta?.creditCosts.AI_EXPLANATION;
  const modelBacked = meta?.aiProvider === 'claude';

  return (
    <div className="space-y-3 px-4 py-3">
      {!modelBacked && (
        <p className="rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-xs text-slate-400">
          No model is configured on this backend, so answers come from the built-in local
          analysis and are free. Set <code className="font-mono">ANTHROPIC_API_KEY</code> to
          enable full explanations.
        </p>
      )}

      <div className="grid grid-cols-2 gap-2">
        {MODES.map(({ mode, label, hint }) => (
          <button
            key={mode}
            type="button"
            className={clsx('btn-ghost flex-col !items-start gap-0.5 !py-2 text-left')}
            onClick={() => void ask(mode)}
            disabled={!trace || pending !== null}
            title={hint}
          >
            <span className="text-xs font-semibold">
              {pending === mode ? 'Thinking...' : label}
            </span>
            <span className="text-[10px] font-normal text-slate-500">{hint}</span>
          </button>
        ))}
      </div>

      <input
        className="field text-xs"
        placeholder="Optional: ask something specific about this step"
        value={question}
        onChange={(event) => setQuestion(event.target.value)}
        maxLength={500}
      />

      {modelBacked && explanationCost != null && (
        <p className="text-[11px] text-slate-500">
          {explanationCost} credits per explanation
          {meta?.creditsEnforced ? '' : ' (metered but not enforced yet)'}. Local fallback answers
          are free.
        </p>
      )}

      {error && (
        <p className="rounded-lg border border-red-900 bg-red-950/50 px-3 py-2 text-xs text-red-300">
          {error}
        </p>
      )}

      {result && (
        <div className="space-y-2 rounded-lg border border-slate-800 bg-slate-950/60 p-3">
          <div className="flex items-center gap-2">
            <span className="chip">{result.provider}</span>
            {result.model && <span className="chip font-mono">{result.model}</span>}
            {result.creditsSpent > 0 && (
              <span className="chip">-{result.creditsSpent} credits</span>
            )}
          </div>
          <MiniMarkdown text={result.explanation} />
        </div>
      )}
    </div>
  );
}
