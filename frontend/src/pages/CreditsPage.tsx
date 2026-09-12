import clsx from 'clsx';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import Panel from '../components/Panel';
import { formatRelativeTime } from '../lib/format';
import type { CreditBalance, CreditTransaction, PageResponse } from '../types';

export default function CreditsPage() {
  const [balance, setBalance] = useState<CreditBalance | null>(null);
  const [ledger, setLedger] = useState<PageResponse<CreditTransaction> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.credits.balance(), api.credits.transactions()])
      .then(([loadedBalance, loadedLedger]) => {
        setBalance(loadedBalance);
        setLedger(loadedLedger);
      })
      .catch(() => setError('Could not load your credits.'));
  }, []);

  if (error) {
    return <p className="p-6 text-sm text-red-300">{error}</p>;
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4 p-4">
      <div>
        <h1 className="text-xl font-bold text-slate-100">Credits</h1>
        <p className="mt-1 text-sm text-slate-400">
          Every change to your balance is a ledger entry, so this page always explains the number
          at the top of the screen.
        </p>
      </div>

      <div className="panel px-4 py-4">
        <p className="text-[10px] uppercase tracking-wide text-slate-500">Balance</p>
        <p className="mt-1 text-3xl font-bold text-slate-100">{balance?.balance ?? '--'}</p>
        {balance && !balance.enforced && (
          <p className="mt-2 text-xs text-slate-500">
            Enforcement is currently off: usage is recorded but nothing is ever refused.
          </p>
        )}
      </div>

      {balance && (
        <Panel title="Price list">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 px-4 py-3 sm:grid-cols-3">
            {Object.entries(balance.costs).map(([action, cost]) => (
              <div key={action}>
                <dt className="text-[10px] uppercase tracking-wide text-slate-500">
                  {action.replace(/_/g, ' ').toLowerCase()}
                </dt>
                <dd className="font-mono text-sm font-semibold text-slate-100">{cost}</dd>
              </div>
            ))}
          </dl>
        </Panel>
      )}

      <Panel title="Ledger">
        {!ledger ? (
          <p className="px-4 py-4 text-sm text-slate-500">Loading...</p>
        ) : (
          <ul className="divide-y divide-slate-800">
            {ledger.content.map((entry) => (
              <li key={entry.id} className="flex items-center gap-3 px-4 py-2 text-xs">
                <span
                  className={clsx(
                    'w-14 shrink-0 font-mono font-bold',
                    entry.amount >= 0 ? 'text-emerald-400' : 'text-rose-400',
                  )}
                >
                  {entry.amount >= 0 ? `+${entry.amount}` : entry.amount}
                </span>
                <span className="chip shrink-0">{entry.type}</span>
                <span className="min-w-0 flex-1 truncate text-slate-400">
                  {entry.description}
                </span>
                <span className="shrink-0 font-mono text-slate-500">
                  = {entry.balanceAfter}
                </span>
                <span className="hidden shrink-0 text-slate-600 sm:inline">
                  {formatRelativeTime(entry.createdAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  );
}
