import clsx from 'clsx';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { DIFFICULTY_CLASSES } from '../lib/format';
import type { Problem } from '../types';

export default function ProblemsPage() {
  const [problems, setProblems] = useState<Problem[] | null>(null);
  const [category, setCategory] = useState<string>('All');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .problems.list()
      .then(setProblems)
      .catch(() => setError('Could not load the problem library.'));
  }, []);

  const categories = useMemo(() => {
    const unique = new Set((problems ?? []).map((problem) => problem.category));
    return ['All', ...Array.from(unique).sort()];
  }, [problems]);

  const visible = (problems ?? []).filter(
    (problem) => category === 'All' || problem.category === category,
  );

  if (error) {
    return <p className="p-6 text-sm text-red-300">{error}</p>;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-4">
      <div>
        <h1 className="text-xl font-bold text-slate-100">Problem library</h1>
        <p className="mt-1 text-sm text-slate-400">
          Working code you can run immediately and step through. Start with bubble sort, then
          compare its counters against selection and insertion sort on the same input.
        </p>
      </div>

      <div className="flex flex-wrap gap-1.5">
        {categories.map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => setCategory(option)}
            className={clsx(
              'rounded-full border px-3 py-1 text-xs transition-colors',
              category === option
                ? 'border-sky-600 bg-sky-600/20 text-sky-200'
                : 'border-slate-700 text-slate-400 hover:text-slate-200',
            )}
          >
            {option}
          </button>
        ))}
      </div>

      {!problems ? (
        <p className="text-sm text-slate-500">Loading...</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {visible.map((problem) => (
            <Link
              key={problem.id}
              to={`/?problem=${problem.slug}`}
              className="panel group flex flex-col gap-2 p-4 transition-colors hover:border-sky-800"
            >
              <div className="flex items-start justify-between gap-3">
                <h2 className="text-sm font-semibold text-slate-100 group-hover:text-sky-300">
                  {problem.title}
                </h2>
                <span
                  className={clsx(
                    'shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-semibold',
                    DIFFICULTY_CLASSES[problem.difficulty] ?? 'text-slate-400 border-slate-700',
                  )}
                >
                  {problem.difficulty}
                </span>
              </div>
              <div className="flex flex-wrap gap-1.5">
                <span className="chip">{problem.category}</span>
                <span className="chip">{problem.language}</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
