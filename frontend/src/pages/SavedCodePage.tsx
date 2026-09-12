import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import Panel from '../components/Panel';
import { formatRelativeTime } from '../lib/format';
import type { PageResponse, SavedCode } from '../types';

export default function SavedCodePage() {
  const [data, setData] = useState<PageResponse<SavedCode> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api
      .savedCode.list()
      .then(setData)
      .catch(() => setError('Could not load your saved code.'));
  }, []);

  useEffect(load, [load]);

  const remove = async (id: number) => {
    await api.savedCode.remove(id);
    load();
  };

  if (error) {
    return <p className="p-6 text-sm text-red-300">{error}</p>;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-3 p-4">
      <h1 className="text-xl font-bold text-slate-100">My code</h1>

      <Panel title={`${data?.totalElements ?? 0} snippets`}>
        {!data ? (
          <p className="px-4 py-4 text-sm text-slate-500">Loading...</p>
        ) : data.content.length === 0 ? (
          <p className="px-4 py-4 text-sm text-slate-500">
            Nothing saved yet. Press Save in the{' '}
            <Link to="/" className="text-sky-400 hover:underline">
              visualizer
            </Link>{' '}
            to keep a snippet.
          </p>
        ) : (
          <ul className="divide-y divide-slate-800">
            {data.content.map((snippet) => (
              <li
                key={snippet.id}
                className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2.5"
              >
                <Link
                  to={`/?saved=${snippet.id}`}
                  className="text-sm text-slate-200 hover:text-sky-300"
                >
                  {snippet.title}
                </Link>
                <span className="chip">{snippet.language}</span>
                {snippet.problemTitle && <span className="chip">{snippet.problemTitle}</span>}

                <span className="ml-auto text-[11px] text-slate-500">
                  updated {formatRelativeTime(snippet.updatedAt)}
                </span>
                <button
                  type="button"
                  className="text-[11px] text-slate-500 hover:text-red-400"
                  onClick={() => void remove(snippet.id)}
                >
                  delete
                </button>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  );
}
