import clsx from 'clsx';
import type { ReactNode } from 'react';

/** A titled panel. Used everywhere so the layout stays consistent without repeating classes. */
export default function Panel({
  title,
  action,
  children,
  className,
  bodyClassName,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section className={clsx('panel flex min-h-0 flex-col', className)}>
      <header className="panel-title">
        <span>{title}</span>
        {action}
      </header>
      <div className={clsx('min-h-0 flex-1 overflow-auto', bodyClassName)}>{children}</div>
    </section>
  );
}
