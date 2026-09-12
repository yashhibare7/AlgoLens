import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import CodeEditor from '../components/CodeEditor';
import Panel from '../components/Panel';
import Tabs, { type TabDefinition } from '../components/Tabs';
import AiPanel from '../components/visualizer/AiPanel';
import CallStackPanel from '../components/visualizer/CallStackPanel';
import JumpControls from '../components/visualizer/JumpControls';
import MetricsPanel from '../components/visualizer/MetricsPanel';
import OutputPanel from '../components/visualizer/OutputPanel';
import PlaybackControls from '../components/visualizer/PlaybackControls';
import StatusBanner from '../components/visualizer/StatusBanner';
import StepDetails from '../components/visualizer/StepDetails';
import Timeline from '../components/visualizer/Timeline';
import VariablePanel from '../components/visualizer/VariablePanel';
import VisualizationPanel from '../components/visualizer/VisualizationPanel';
import { usePlayer } from '../hooks/usePlayer';
import { DEFAULT_SAMPLE } from '../lib/samples';
import { useAuth } from '../store/AuthContext';
import type { ExecutionTrace } from '../types';

export default function VisualizerPage() {
  const { user, meta, setCreditBalance } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [code, setCode] = useState(DEFAULT_SAMPLE);
  const [language, setLanguage] = useState('JAVA');
  const [title, setTitle] = useState('Untitled');
  const [problemId, setProblemId] = useState<number | undefined>();
  const [savedCodeId, setSavedCodeId] = useState<number | undefined>();

  const [trace, setTrace] = useState<ExecutionTrace | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pinnedLine, setPinnedLine] = useState<number | null>(null);
  const [breakpoints, setBreakpoints] = useState<ReadonlySet<number>>(new Set());
  const [activeTab, setActiveTab] = useState('variables');

  const player = usePlayer(trace, { breakpoints });

  // A line pinned from the error banner wins until the user steps again.
  const highlightLine = pinnedLine ?? player.step?.line ?? null;
  useEffect(() => setPinnedLine(null), [player.index]);

  const toggleBreakpoint = useCallback((line: number) => {
    setBreakpoints((current) => {
      const next = new Set(current);
      if (!next.delete(line)) {
        next.add(line);
      }
      return next;
    });
  }, []);

  // ------------------------------------------------------------------ deep links

  useEffect(() => {
    const problemSlug = searchParams.get('problem');
    const executionId = searchParams.get('execution');
    const savedId = searchParams.get('saved');

    const load = async () => {
      try {
        if (problemSlug) {
          const problem = await api.problems.detail(problemSlug);
          setCode(problem.starterCode ?? DEFAULT_SAMPLE);
          setLanguage(problem.language);
          setTitle(problem.title);
          setProblemId(problem.id);
          setSavedCodeId(undefined);
          setTrace(null);
        } else if (savedId) {
          const saved = await api.savedCode.detail(Number(savedId));
          setCode(saved.code ?? '');
          setLanguage(saved.language);
          setTitle(saved.title);
          setSavedCodeId(saved.id);
          setProblemId(saved.problemId);
          setTrace(null);
        } else if (executionId) {
          const detail = await api.executions.detail(Number(executionId));
          setCode(detail.code);
          setLanguage(detail.language);
          setTitle(`Replay #${detail.id}`);
          // The stored trace replays without re-running anything.
          setTrace(detail.trace ?? null);
          if (!detail.trace) {
            setNotice(
              'This run is too large to have been stored for replay. Press Run to regenerate it.',
            );
          }
        }
      } catch (caught) {
        setError(caught instanceof ApiError ? caught.message : 'Could not load that link.');
      }
    };

    void load();
    // Intentionally keyed on the raw query string: re-running on every searchParams identity
    // change would refetch on unrelated state updates.
  }, [searchParams.toString()]);

  // ------------------------------------------------------------------ run

  const run = useCallback(async () => {
    setRunning(true);
    setError(null);
    setNotice(null);
    try {
      const response = await api.executions.run({ language, code, problemId, savedCodeId });
      setTrace(response.trace);
      if (response.creditBalance != null) {
        setCreditBalance(response.creditBalance);
      }
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(
          caught.isOutOfCredits
            ? `${caught.message} (needed ${caught.details?.required}, had ${caught.details?.available})`
            : caught.message,
        );
      } else {
        setError('Something went wrong running that code.');
      }
      setTrace(null);
    } finally {
      setRunning(false);
    }
  }, [code, language, problemId, savedCodeId, setCreditBalance]);

  const save = useCallback(async () => {
    setError(null);
    try {
      const payload = { title: title || 'Untitled', language, code, problemId };
      const saved = savedCodeId
        ? await api.savedCode.update(savedCodeId, payload)
        : await api.savedCode.create(payload);
      setSavedCodeId(saved.id);
      setNotice(`Saved as "${saved.title}".`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not save that.');
    }
  }, [code, language, problemId, savedCodeId, title]);

  // ------------------------------------------------------------------ offered fixes

  /**
   * A `main` method the backend generated for a class that has none.
   *
   * <p>Pasting a LeetCode answer -- a bare `class Solution` with one method -- is the single
   * most common way to hit a dead end here, so the backend answers with a runnable `main` and
   * this turns it into one click.
   */
  const suggestedMain = useMemo(() => {
    const message = trace?.errorMessage;
    if (!message) {
      return null;
    }
    const start = message.indexOf('    public static void main');
    return start >= 0 ? message.slice(start) : null;
  }, [trace]);

  const insertSuggestedMain = useCallback(() => {
    if (!suggestedMain) {
      return;
    }
    // Goes inside the last class body, which is where a `main` belongs.
    const lastBrace = code.lastIndexOf('}');
    setCode(
      lastBrace < 0
        ? `${code}\n\n${suggestedMain}\n`
        : `${code.slice(0, lastBrace)}\n${suggestedMain}\n${code.slice(lastBrace)}`,
    );
    setTrace(null);
    setNotice('Added a main method with sample input. Edit the values, then press Run.');
  }, [code, suggestedMain]);

  // ------------------------------------------------------------------ keyboard

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      // Ctrl/Cmd+Enter runs from anywhere, including inside the editor.
      if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        event.preventDefault();
        void run();
        return;
      }

      // The transport keys must not fight with typing, so they only apply when focus is not
      // in the editor or a form field.
      const target = event.target as HTMLElement | null;
      const typing =
        target?.closest('.monaco-editor') != null ||
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement;
      if (typing) {
        return;
      }

      switch (event.key) {
        case ' ':
          event.preventDefault();
          player.toggle();
          break;
        case 'ArrowRight':
          event.preventDefault();
          player.next();
          break;
        case 'ArrowLeft':
          event.preventDefault();
          player.previous();
          break;
        case 'Home':
          event.preventDefault();
          player.reset();
          break;
        case 'End':
          event.preventDefault();
          player.jumpToEnd();
          break;
        default:
          break;
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [player, run]);

  // ------------------------------------------------------------------ inspector tabs

  const step = player.step;
  const variableCount = step?.variables?.length ?? 0;
  const frameCount = step?.callStack?.length ?? 0;
  const outputLines = player.outputSoFar ? player.outputSoFar.trimEnd().split('\n').length : 0;

  const tabs: TabDefinition[] = useMemo(
    () => [
      {
        id: 'variables',
        label: 'Variables',
        badge: variableCount || undefined,
        content: <VariablePanel variables={step?.variables ?? []} />,
      },
      {
        id: 'output',
        label: 'Output',
        badge: outputLines || undefined,
        content: <OutputPanel output={player.outputSoFar} />,
      },
      {
        id: 'stack',
        label: 'Call stack',
        badge: frameCount || undefined,
        content:
          frameCount > 0 ? (
            <CallStackPanel frames={step?.callStack ?? []} />
          ) : (
            <p className="px-4 py-3 text-xs text-slate-500">
              No method call is active. This panel fills in while execution is inside a method.
            </p>
          ),
      },
      {
        id: 'metrics',
        label: 'Metrics',
        content: trace ? (
          <MetricsPanel metrics={trace.metrics} durationMs={trace.durationMs} />
        ) : (
          <p className="px-4 py-3 text-xs text-slate-500">Run the code to collect metrics.</p>
        ),
      },
      {
        id: 'ai',
        label: 'AI',
        content: (
          <AiPanel trace={trace} code={code} language={language} stepIndex={player.index} />
        ),
      },
    ],
    [step, trace, code, language, player.index, player.outputSoFar, variableCount, frameCount,
      outputLines],
  );

  // ------------------------------------------------------------------ render

  const languages = meta?.languages ?? [{ id: 'JAVA', displayName: 'Java', supported: true }];

  return (
    <div className="grid h-full min-h-0 gap-3 p-3 lg:grid-cols-2">
      {/* ---------------- editor column ---------------- */}
      <div className="flex min-h-0 flex-col gap-2.5">
        <div className="flex flex-wrap items-center gap-2">
          <select
            className="field w-auto py-1.5 text-xs"
            value={language}
            onChange={(event) => setLanguage(event.target.value)}
            aria-label="Language"
          >
            {languages.map((option) => (
              <option key={option.id} value={option.id} disabled={!option.supported}>
                {option.displayName}
                {option.supported ? '' : ' (coming soon)'}
              </option>
            ))}
          </select>

          <input
            className="field w-36 py-1.5 text-xs"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            aria-label="Title"
            placeholder="Title"
          />

          <button type="button" className="btn-primary" onClick={() => void run()} disabled={running}>
            {running ? 'Running...' : '▶ Run'}
            <span className="hidden text-[10px] opacity-70 sm:inline">Ctrl+Enter</span>
          </button>

          {user && (
            <button type="button" className="btn-ghost" onClick={() => void save()}>
              {savedCodeId ? 'Update' : 'Save'}
            </button>
          )}

          <button
            type="button"
            className="btn-ghost"
            onClick={() => {
              setCode(DEFAULT_SAMPLE);
              setTrace(null);
              setProblemId(undefined);
              setSavedCodeId(undefined);
              setTitle('Untitled');
              setBreakpoints(new Set());
              setSearchParams({});
            }}
            title="Load the bubble sort sample"
          >
            Sample
          </button>

          {breakpoints.size > 0 && (
            <button
              type="button"
              className="btn-ghost !py-1.5 text-[11px]"
              onClick={() => setBreakpoints(new Set())}
              title="Remove all breakpoints"
            >
              Clear {breakpoints.size} breakpoint{breakpoints.size === 1 ? '' : 's'}
            </button>
          )}
        </div>

        {error && (
          <p className="rounded-lg border border-red-900 bg-red-950/50 px-3 py-2 text-xs text-red-300">
            {error}
          </p>
        )}
        {notice && (
          <p className="rounded-lg border border-sky-900 bg-sky-950/40 px-3 py-2 text-xs text-sky-300">
            {notice}
          </p>
        )}

        <div className="panel min-h-[240px] flex-1 overflow-hidden">
          <CodeEditor
            value={code}
            onChange={setCode}
            highlightLine={highlightLine}
            language={language.toLowerCase() === 'cpp' ? 'cpp' : language.toLowerCase()}
            breakpoints={breakpoints}
            onToggleBreakpoint={toggleBreakpoint}
          />
        </div>

        {/* Playback stays pinned below the editor so it never scrolls out of reach. */}
        {trace && (
          <div className="shrink-0 space-y-2">
            <StatusBanner
              trace={trace}
              onJumpToLine={setPinnedLine}
              action={
                suggestedMain ? (
                  <button
                    type="button"
                    className="btn-primary !py-1 text-[11px]"
                    onClick={insertSuggestedMain}
                  >
                    Add this main method
                  </button>
                ) : undefined
              }
            />

            {trace.steps.length > 0 && (
              <>
                <Timeline trace={trace} index={player.index} onSeek={player.seek} />
                <PlaybackControls player={player} />
                <JumpControls trace={trace} player={player} />
                {player.pausedAtBreakpoint && (
                  <p className="text-[11px] text-rose-300">
                    Paused at a breakpoint on line {player.step?.line}. Press Play to continue.
                  </p>
                )}
                {breakpoints.size === 0 && trace.steps.length > 60 && (
                  <p className="text-[11px] text-slate-500">
                    Long run — click a line number's left margin to set a breakpoint, or use the
                    jump buttons above.
                  </p>
                )}
              </>
            )}
          </div>
        )}
      </div>

      {/* ---------------- inspector column ---------------- */}
      <div className="flex min-h-0 flex-col gap-2.5">
        {/* The visualization takes the space; it is the thing being watched. */}
        <Panel title="Visualization" className="min-h-[220px] flex-1" bodyClassName="overflow-auto">
          <VisualizationPanel step={player.step} />
        </Panel>

        {/* Always visible, never a tab: this is the caption for the picture above. */}
        <div className="panel shrink-0">
          <StepDetails step={player.step} />
        </div>

        <Tabs
          tabs={tabs}
          active={activeTab}
          onChange={setActiveTab}
          className="h-[38%] min-h-[190px] shrink-0"
        />
      </div>
    </div>
  );
}
