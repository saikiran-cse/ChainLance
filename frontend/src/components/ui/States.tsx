import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { CircleAlert, Construction, FlaskConical, WifiOff } from 'lucide-react';
import { env } from '../../config/env';
import { useAuth } from '../../hooks/useAuth';
import type { AppError } from '../../utils/errors';
import { cn } from '../../utils/cn';
import { Button } from './Button';

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded-sm bg-stone', className)} />;
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading" className="flex flex-col gap-3">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-16 w-full" />
      ))}
    </div>
  );
}

export function EmptyState({
  title,
  children,
  action,
  className,
}: {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('rounded-lg border border-dashed border-warm-grey px-6 py-10 text-center', className)}>
      <p className="font-display text-2xl text-ink">{title}</p>
      {children && <p className="mx-auto mt-2 max-w-md text-mute">{children}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  );
}

/**
 * Shown when a request failed. A "pending" error means the backend has no handler for that URL yet,
 * which is expected today for marketplace endpoints: it is explained, not presented as a crash.
 */
export function ErrorState({ error, onRetry, className }: { error: AppError; onRetry?: () => void; className?: string }) {
  const { status } = useAuth();

  if (error.kind === 'pending') {
    return (
      <div className={cn('rounded-lg border border-ochre bg-paper-raised p-6', className)} role="status">
        <div className="flex items-start gap-3">
          <Construction aria-hidden className="mt-1 size-5 shrink-0 text-ochre" />
          <div>
            <p className="font-display text-2xl text-ink">Not available in the backend yet</p>
            <p className="mt-2 max-w-xl text-mute">
              This page is built and waiting for{' '}
              <code className="rounded-sm bg-stone px-1.5 py-0.5 font-mono text-sm text-ink">
                {error.method ?? 'GET'} {error.url ?? 'this endpoint'}
              </code>
              , which the backend does not expose yet. Nothing is wrong with your account or connection.
            </p>
            <div className="mt-4 flex flex-wrap gap-3">
              <Link to="/api-status" className="text-sm font-medium underline underline-offset-4 hover:text-mute">
                See which endpoints exist
              </Link>
              {onRetry && (
                <button
                  type="button"
                  onClick={onRetry}
                  className="text-sm font-medium underline underline-offset-4 hover:text-mute"
                >
                  Check again
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }

  const Icon = error.kind === 'network' ? WifiOff : CircleAlert;
  const anonymousDenied = error.kind === 'unauthorized' && status !== 'authenticated';
  return (
    <div className={cn('rounded-lg border border-brick bg-paper-raised p-6', className)} role="alert">
      <div className="flex items-start gap-3">
        <Icon aria-hidden className="mt-1 size-5 shrink-0 text-brick" />
        <div>
          <p className="font-display text-2xl text-ink">
            {error.kind === 'network' ? 'Cannot reach the API' : anonymousDenied ? 'Sign in to continue' : 'That did not work'}
          </p>
          <p className="mt-2 max-w-xl text-mute">
            {anonymousDenied
              ? 'The backend currently requires an account for this data, so it cannot be shown to signed-out visitors.'
              : error.message}
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            {anonymousDenied && (
              <Link to="/login" className="text-sm font-medium underline underline-offset-4">
                Sign in
              </Link>
            )}
            {onRetry && (
              <Button size="sm" variant="outline" onClick={onRetry}>
                Try again
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/** Persistent notice while labelled sample data replaces the missing backend endpoints. */
export function SampleBanner() {
  if (!env.useMocks) return null;
  return (
    <div role="note" className="on-ink border-b border-ink bg-ink px-4 py-2 text-sm text-paper">
      <div className="mx-auto flex max-w-7xl items-center gap-2">
        <FlaskConical aria-hidden className="size-4 shrink-0 text-lime" />
        <p>
          Sample data is on. Projects, proposals, negotiations and agreements shown here are invented and reset on reload.
          Sign-in, profile and wallet still use the real API.
        </p>
      </div>
    </div>
  );
}

/** Small inline marker for a feature whose backend endpoint is not built yet. */
export function PendingTag({ children = 'Backend pending' }: { children?: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-sm border border-ochre px-1.5 py-0.5 text-xs font-medium text-ochre">
      <Construction aria-hidden className="size-3" />
      {children}
    </span>
  );
}
