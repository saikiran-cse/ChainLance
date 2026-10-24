import type { ReactNode } from 'react';
import { cn } from '../../utils/cn';

export function PageHeader({
  title,
  children,
  actions,
  className,
}: {
  title: string;
  children?: ReactNode;
  actions?: ReactNode;
  className?: string;
}) {
  return (
    <header className={cn('flex flex-col gap-4 border-b border-line pb-6 sm:flex-row sm:items-end sm:justify-between', className)}>
      <div className="min-w-0">
        <h1 className="font-display text-4xl leading-[1.05] text-ink sm:text-5xl">{title}</h1>
        {children && <p className="mt-3 max-w-2xl text-mute">{children}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-3">{actions}</div>}
    </header>
  );
}

export function Panel({
  title,
  action,
  children,
  className,
  padded = true,
}: {
  title?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return (
    <section className={cn('rounded-lg border border-line bg-paper-raised', className)}>
      {(title || action) && (
        <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-3.5">
          {title && <h2 className="text-lg font-semibold text-ink">{title}</h2>}
          {action}
        </div>
      )}
      <div className={cn(padded && 'p-5')}>{children}</div>
    </section>
  );
}

/** A figure with a plain label. Used for real counts only. */
export function Stat({ label, value, note }: { label: string; value: ReactNode; note?: string }) {
  return (
    <div className="flex flex-col gap-1 border-l-2 border-ink pl-4">
      <span className="text-sm text-mute">{label}</span>
      <span className="font-display text-4xl leading-none text-ink">{value}</span>
      {note && <span className="text-xs text-mute">{note}</span>}
    </div>
  );
}

export function DefinitionRow({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 border-b border-line py-3 last:border-b-0 sm:grid-cols-[11rem_1fr] sm:gap-6">
      <dt className="text-sm text-mute">{term}</dt>
      <dd className="min-w-0 break-words text-ink">{children}</dd>
    </div>
  );
}
