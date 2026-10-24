import { cn } from '../../utils/cn';
import { humanizeEnum } from '../../utils/format';

type Tone = 'lime' | 'ink' | 'stone' | 'brick' | 'ochre';

const TONES: Record<Tone, string> = {
  lime: 'bg-lime text-ink border-ink',
  ink: 'bg-ink text-paper border-ink',
  stone: 'bg-stone text-ink-soft border-line',
  brick: 'bg-transparent text-brick border-brick',
  ochre: 'bg-transparent text-ochre border-ochre',
};

const STATUS_TONE: Record<string, Tone> = {
  // projects
  DRAFT: 'stone',
  OPEN: 'lime',
  IN_PROGRESS: 'ink',
  COMPLETED: 'ink',
  CLOSED: 'stone',
  CANCELLED: 'brick',
  // proposals
  SUBMITTED: 'stone',
  SHORTLISTED: 'ochre',
  ACCEPTED: 'lime',
  REJECTED: 'brick',
  WITHDRAWN: 'stone',
  // agreements
  FINALIZED: 'lime',
  FUNDED: 'ink',
};

/** Compact status label. The tone only supports the text; the words always carry the meaning. */
export function StatusBadge({ status, className }: { status: string; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-sm border px-2 py-0.5 text-xs font-medium whitespace-nowrap',
        TONES[STATUS_TONE[status] ?? 'stone'],
        className,
      )}
    >
      {humanizeEnum(status)}
    </span>
  );
}
