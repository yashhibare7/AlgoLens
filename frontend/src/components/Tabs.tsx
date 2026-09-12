import clsx from 'clsx';
import type { ReactNode } from 'react';

export interface TabDefinition {
  id: string;
  label: string;
  /** Shown as a small count or marker next to the label. */
  badge?: string | number;
  content: ReactNode;
}

/**
 * A tab strip for the inspector panels.
 *
 * <p>Replaces a vertical stack of panels. Stacking looked fine at rest and failed in motion:
 * during playback the variables, output and call stack all changed at once, but only whichever
 * happened to be scrolled into view could be seen — and each panel having its own inner
 * scrollbar made it worse. Tabs guarantee that whatever is selected is fully visible, and the
 * badges show activity on the ones that are not.
 */
export default function Tabs({
  tabs,
  active,
  onChange,
  className,
}: {
  tabs: TabDefinition[];
  active: string;
  onChange: (id: string) => void;
  className?: string;
}) {
  const current = tabs.find((tab) => tab.id === active) ?? tabs[0];

  return (
    <section className={clsx('panel flex min-h-0 flex-col', className)}>
      <div
        role="tablist"
        className="flex shrink-0 items-center gap-0.5 overflow-x-auto border-b border-slate-800 px-1.5 py-1"
      >
        {tabs.map((tab) => (
          <button
            key={tab.id}
            role="tab"
            type="button"
            aria-selected={tab.id === current?.id}
            onClick={() => onChange(tab.id)}
            className={clsx(
              'flex shrink-0 items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
              tab.id === current?.id
                ? 'bg-slate-800 text-slate-100'
                : 'text-slate-400 hover:bg-slate-800/50 hover:text-slate-200',
            )}
          >
            {tab.label}
            {tab.badge !== undefined && tab.badge !== '' && (
              <span
                className={clsx(
                  'rounded px-1 py-px text-[10px] font-mono',
                  tab.id === current?.id
                    ? 'bg-slate-700 text-slate-200'
                    : 'bg-slate-800 text-slate-500',
                )}
              >
                {tab.badge}
              </span>
            )}
          </button>
        ))}
      </div>

      <div role="tabpanel" className="min-h-0 flex-1 overflow-auto">
        {current?.content}
      </div>
    </section>
  );
}
