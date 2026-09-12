import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import Panel from '../components/Panel';
import { formatDuration, formatRelativeTime, STATUS_LABELS } from '../lib/format';
import type { ExecutionSummary, PageResponse } from '../types';

export default function HistoryPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<ExecutionSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    (target: number) => {
      api
        .executions.list(target)
        .then(setData)
        .catch(() => setError('Could not load your history.'));
    },
    [],
  );

  useEffect(() => load(page), [load, page]);

  const remove = async (id: number) => {
    await api.executions.remove(id);
    load(page);
  };

  if (error) {
    return <p className="p-6 text-sm text-red-300">{error}</p>;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-3 p-4">
      <h1 className="text-xl font-bold text-slate-100">Run history</h1>
      <p className="text-sm text-slate-400">
        Every run is stored with its trace, so opening one replays the exact same execution --
        nothing is re-run and it costs no credits.
      </p>

      <Panel title={`${data?.totalElements ?? 0} runs`}>
        {!data ? (
          <p className="px-4 py-4 text-sm text-slate-500">Loading...</p>
        ) : data.content.length === 0 ? (
          <p className="px-4 py-4 text-sm text-slate-500">
            No runs yet.{' '}
            <Link to="/" className="text-sky-400 hover:underline">
              Go run something
            </Link>
            .
          </p>
        ) : (
          <ul className="divide-y divide-slate-800">
            {data.content.map((execution) => (
              <li
                key={execution.id}
                className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2.5"
              >
                <Link
                  to={`/?execution=${execution.id}`}
                  className="text-sm text-slate-200 hover:text-sky-300"
                >
                  {execution.title}
                </Link>
                <span className="chip">{execution.language}</span>
                <span
                  className={
                    execution.status === 'SUCCESS'
                      ? 'text-[11px] text-emerald-400'
                      : 'text-[11px] text-amber-400'
                  }
                >
                  {STATUS_LABELS[execution.status]}
                </span>
                {!execution.replayable && (
                  <span className="chip text-slate-500" title="The trace was too large to store">
                    not replayable
                  </span>
                )}

                <span className="ml-auto text-[11px] text-slate-500">
                  {execution.totalSteps} steps &middot; {formatDuration(execution.durationMs)}{' '}
                  &middot; -{execution.creditsSpent} credits &middot;{' '}
                  {formatRelativeTime(execution.createdAt)}
                </span>

                <button
                  type="button"
                  className="text-[11px] text-slate-500 hover:text-red-400"
                  onClick={() => void remove(execution.id)}
                >
                  delete
                </button>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="btn-ghost"
            disabled={page === 0}
            onClick={() => setPage((current) => current - 1)}
          >
            Previous
          </button>
          <span className="text-xs text-slate-500">
            page {data.page + 1} of {data.totalPages}
          </span>
          <button
            type="button"
            className="btn-ghost"
            disabled={data.last}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
