import type { NegotiationMessage, Proposal, ProposalStatus } from '../../types/marketplace';
import { cn } from '../../utils/cn';
import { formatDateTime, formatInr, initials } from '../../utils/format';

type OfferState = 'Pending' | 'Accepted' | 'Rejected' | 'Superseded';

function offerLabel(isLatestOffer: boolean, status: ProposalStatus): OfferState {
  if (!isLatestOffer) return 'Superseded';
  if (status === 'ACCEPTED') return 'Accepted';
  if (status === 'REJECTED' || status === 'WITHDRAWN') return 'Rejected';
  return 'Pending';
}

const OFFER_TONE: Record<OfferState, string> = {
  Pending: 'border-ochre text-ochre',
  Accepted: 'border-ink bg-lime text-ink',
  Rejected: 'border-brick text-brick',
  Superseded: 'border-line text-mute',
};

function Terms({ priceInr, durationDays, previous }: { priceInr?: number; durationDays?: number; previous?: { priceInr?: number } }) {
  return (
    <dl className="flex flex-wrap gap-x-8 gap-y-2">
      {priceInr != null && (
        <div>
          <dt className="text-xs text-mute">Price</dt>
          <dd className="font-display text-3xl leading-none">{formatInr(priceInr)}</dd>
          {previous?.priceInr != null && previous.priceInr !== priceInr && (
            <dd className="mt-1 text-xs text-mute">was {formatInr(previous.priceInr)}</dd>
          )}
        </div>
      )}
      {durationDays != null && (
        <div>
          <dt className="text-xs text-mute">Duration</dt>
          <dd className="font-display text-3xl leading-none">{durationDays} days</dd>
        </div>
      )}
    </dl>
  );
}

/**
 * The conversation, oldest first. The proposal itself is the first offer, and each later message that carries terms
 * is a counteroffer. Only the newest offer can be pending; the backend's proposal status decides if it was accepted.
 */
export function OfferThread({
  proposal,
  messages,
  currentUserId,
}: {
  proposal: Proposal;
  messages: NegotiationMessage[];
  currentUserId: number;
}) {
  const sorted = [...messages].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const lastOfferId = [...sorted].reverse().find((m) => m.proposedTerms && (m.proposedTerms.priceInr != null || m.proposedTerms.durationDays != null))?.id;
  const openingIsLatest = lastOfferId === undefined;

  let runningPrice = proposal.priceInr;

  return (
    <ol className="flex flex-col gap-5" aria-label="Negotiation history">
      <li className="rounded-lg border border-ink bg-paper-raised p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-medium">
            {proposal.freelancerName ?? 'Freelancer'} <span className="font-normal text-mute">sent the opening proposal</span>
          </p>
          <span className={cn('rounded-sm border px-2 py-0.5 text-xs font-medium', OFFER_TONE[offerLabel(openingIsLatest, proposal.status)])}>
            {offerLabel(openingIsLatest, proposal.status)}
          </span>
        </div>
        <div className="mt-4">
          <Terms priceInr={proposal.priceInr} durationDays={proposal.durationDays} />
        </div>
        <p className="mt-4 whitespace-pre-line text-ink-soft">{proposal.message}</p>
        <p className="mt-3 text-xs text-mute">{formatDateTime(proposal.createdAt)}</p>
      </li>

      {sorted.map((m) => {
        const mine = m.senderId === currentUserId;
        const hasTerms = !!m.proposedTerms && (m.proposedTerms.priceInr != null || m.proposedTerms.durationDays != null);
        const previousPrice = runningPrice;
        if (hasTerms && m.proposedTerms?.priceInr != null) runningPrice = m.proposedTerms.priceInr;
        const state = hasTerms ? offerLabel(m.id === lastOfferId, proposal.status) : null;
        return (
          <li key={m.id} className={cn('flex flex-col gap-1', mine ? 'items-end' : 'items-start')}>
            <div
              className={cn(
                'w-full max-w-2xl rounded-lg border p-5',
                mine ? 'border-ink bg-lime' : 'border-line bg-paper-raised',
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="flex items-center gap-2 text-sm font-medium">
                  <span
                    aria-hidden
                    className="grid size-6 place-items-center rounded-sm bg-ink text-[10px] font-semibold text-paper"
                  >
                    {initials(m.senderName)}
                  </span>
                  {mine ? 'You' : (m.senderName ?? 'Other party')}
                  {hasTerms && <span className="font-normal text-ink-soft">made a counteroffer</span>}
                </p>
                {state && (
                  <span className={cn('rounded-sm border bg-paper px-2 py-0.5 text-xs font-medium', OFFER_TONE[state])}>{state}</span>
                )}
              </div>
              {hasTerms && (
                <div className="mt-4">
                  <Terms
                    priceInr={m.proposedTerms?.priceInr}
                    durationDays={m.proposedTerms?.durationDays}
                    previous={{ priceInr: previousPrice }}
                  />
                </div>
              )}
              {m.message && <p className="mt-3 whitespace-pre-line">{m.message}</p>}
              <p className="mt-3 text-xs text-ink-soft">{formatDateTime(m.createdAt)}</p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
