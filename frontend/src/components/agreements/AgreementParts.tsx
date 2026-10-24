import { BadgeCheck, CircleDashed } from 'lucide-react';
import type { Agreement, AgreementMilestone } from '../../types/marketplace';
import { formatDate, formatDateTime, formatInr, humanizeEnum, shortAddress, weiToEth } from '../../utils/format';
import { EmptyState } from '../ui/States';
import { StatusBadge } from '../ui/StatusBadge';

/** Who has finalized. Both timestamps come from the backend; the UI never infers them. */
export function FinalizationStatus({ agreement }: { agreement: Agreement }) {
  const rows = [
    { who: agreement.clientName ?? 'Client', at: agreement.clientFinalizedAt },
    { who: agreement.freelancerName ?? 'Freelancer', at: agreement.freelancerFinalizedAt },
  ];
  return (
    <ul className="flex flex-col gap-3">
      {rows.map((r) => (
        <li key={r.who} className="flex items-start gap-3">
          {r.at ? (
            <BadgeCheck aria-hidden className="mt-0.5 size-5 shrink-0" />
          ) : (
            <CircleDashed aria-hidden className="mt-0.5 size-5 shrink-0 text-mute" />
          )}
          <div>
            <p className="font-medium">{r.who}</p>
            <p className="text-sm text-mute">{r.at ? `Finalized ${formatDateTime(r.at)}` : 'Has not finalized yet'}</p>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function MilestoneList({ milestones }: { milestones: AgreementMilestone[] }) {
  if (milestones.length === 0) {
    return <EmptyState title="No milestones recorded">The backend has not returned a milestone plan for this agreement.</EmptyState>;
  }
  return (
    <ol>
      {milestones.map((m) => (
        <li key={m.id ?? m.position} className="grid gap-2 border-b border-line py-4 last:border-b-0 sm:grid-cols-[2.5rem_1fr_auto] sm:gap-4">
          <span className="font-display text-3xl leading-none">{m.position}</span>
          <div className="min-w-0">
            <p className="font-medium">{m.description}</p>
            {m.acceptanceCriteria && <p className="mt-1 text-sm text-mute">Accepted when: {m.acceptanceCriteria}</p>}
            <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-mute">
              {m.effectiveDeadline && <span>Due {formatDate(m.effectiveDeadline)}</span>}
              {m.workStatus && <StatusBadge status={m.workStatus} />}
              {/* On-chain state is shown as reported by the backend after it verified a transaction. */}
              {m.chainStatus && <span>On-chain: {humanizeEnum(m.chainStatus)}</span>}
            </div>
          </div>
          <div className="text-left sm:text-right">
            {m.amountInr != null && <p className="font-semibold">{formatInr(m.amountInr)}</p>}
            {m.amountWei && <p className="text-sm text-mute">{weiToEth(m.amountWei)} ETH</p>}
          </div>
        </li>
      ))}
    </ol>
  );
}

/** The frozen terms. Hash and JSON are displayed as text so they can be compared with the on-chain project id. */
export function SnapshotView({ agreement }: { agreement: Agreement }) {
  if (!agreement.snapshot && !agreement.snapshotHash) {
    return (
      <p className="text-mute">
        No snapshot yet. The backend creates one when both parties have finalized the agreed terms.
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {agreement.snapshotHash && (
        <div>
          <p className="text-sm text-mute">Snapshot hash</p>
          <p className="mt-1 break-all font-mono text-sm">{agreement.snapshotHash}</p>
        </div>
      )}
      {agreement.snapshot && (
        <details className="rounded-md border border-line bg-paper">
          <summary className="cursor-pointer px-4 py-3 text-sm font-medium">Agreed-term snapshot (JSON)</summary>
          <pre className="max-h-96 overflow-auto border-t border-line p-4 font-mono text-xs leading-relaxed">
            {JSON.stringify(agreement.snapshot, null, 2)}
          </pre>
        </details>
      )}
    </div>
  );
}

export function EscrowDetails({ agreement }: { agreement: Agreement }) {
  const funded = agreement.status === 'FUNDED' || agreement.status === 'COMPLETED';
  return (
    <dl className="grid gap-4 sm:grid-cols-2">
      <div>
        <dt className="text-sm text-mute">Escrow contract</dt>
        <dd className="mt-1 break-all font-mono text-sm">{agreement.escrowAddress ?? 'Not created yet'}</dd>
      </div>
      <div>
        <dt className="text-sm text-mute">Funding</dt>
        <dd className="mt-1">{funded ? 'Funded, as verified by the backend' : 'Not funded'}</dd>
      </div>
      <div>
        <dt className="text-sm text-mute">Client wallet</dt>
        <dd className="mt-1 font-mono text-sm">{agreement.clientWallet ? shortAddress(agreement.clientWallet) : '—'}</dd>
      </div>
      <div>
        <dt className="text-sm text-mute">Freelancer wallet</dt>
        <dd className="mt-1 font-mono text-sm">{agreement.freelancerWallet ? shortAddress(agreement.freelancerWallet) : '—'}</dd>
      </div>
    </dl>
  );
}
