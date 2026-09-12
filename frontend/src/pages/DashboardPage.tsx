import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import Panel from '../components/Panel';
import { formatDuration, formatRelativeTime, STATUS_LABELS } from '../lib/format';
import type { DashboardResponse } from '../types';

export default function DashboardPage() {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .dashboard()
      .then(setData)
      .catch(() => setError('Could not load your dashboard.'));
  }, []);

  if (error) {
    return <p className="p-6 text-sm text-red-300">{error}</p>;
  }
  if (!data) {
    return <p className="p-6 text-sm text-slate-500">Loading...</p>;
  }

  const stats: Array<[string, string | number]> = [
    ['Runs', data.totalExecutions],
    ['Successful', data.successfulExecutions],
    ['Problems solved', data.problemsSolved],
    ['Saved snippets', data.savedSnippets],
    ['Credits', data.creditBalance],
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-4">
      <div>
        <h1 className="text-xl font-bold text-slate-100">Hi {data.user.name}</h1>
        <p className="text-sm text-slate-400">{data.user.email}</p>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {stats.map(([label, value]) => (
          <div key={label} className="panel px-4 py-3">
            <p className="text-[10px] uppercase tracking-wide text-slate-500">{label}</p>
            <p className="mt-1 text-2xl font-bold text-slate-100">{value}</p>
          </div>
        ))}
      </div>

      <Panel
        title="Recent activity"
        action={
          <Link to="/history" className="text-[11px] normal-case text-sky-400 hover:underline">
            See all
          </Link>
        }
      >
        {data.recentExecutions.length === 0 ? (
          <p className="px-4 py-4 text-sm text-slate-500">
            Nothing yet.{' '}
            <Link to="/" className="text-sky-400 hover:underline">
              Run some code
            </Link>{' '}
            to get started.
          </p>
        ) : (
          <ul className="divide-y divide-slate-800">
            {data.recentExecutions.map((execution) => (
              <li key={execution.id}>
                <Link
                  to={`/?execution=${execution.id}`}
                  className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2.5
                    hover:bg-slate-800/40"
                >
                  <span className="text-sm text-slate-200">{execution.title}</span>
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
                  <span className="ml-auto text-[11px] text-slate-500">
                    {execution.totalSteps} steps &middot; {formatDuration(execution.durationMs)}{' '}
                    &middot; {formatRelativeTime(execution.createdAt)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  );
}
