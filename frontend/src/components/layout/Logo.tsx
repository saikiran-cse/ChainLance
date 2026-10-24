import { Link } from 'react-router-dom';
import { cn } from '../../utils/cn';

/** Two interlocked links drawn as plain squares, next to the wordmark. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 28 28" aria-hidden className={cn('size-7', className)}>
      <rect x="1.5" y="8.5" width="15" height="15" rx="2" fill="none" stroke="currentColor" strokeWidth="2" />
      <rect x="11.5" y="4.5" width="15" height="15" rx="2" fill="#c6f432" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}

export function Logo({ to = '/', className }: { to?: string; className?: string }) {
  return (
    <Link to={to} className={cn('inline-flex items-center gap-2.5 text-ink', className)} aria-label="ChainLance home">
      <LogoMark />
      <span className="font-display text-[1.65rem] leading-none tracking-tight">ChainLance</span>
    </Link>
  );
}
