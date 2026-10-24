import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { Proposal } from '../../types/marketplace';
import { formatInr, timeAgo } from '../../utils/format';
import { StatusBadge } from '../ui/StatusBadge';

/** One proposal as a compact row. `to` decides where the title leads (negotiation, review, project). */
export function ProposalRow({
  proposal,
  to,
  showFreelancer,
  trailing,
}: {
  proposal: Proposal;
  to: string;
  showFreelancer?: boolean;
  trailing?: ReactNode;
}) {
  return (
    <li className="grid gap-2 border-b border-line py-4 last:border-b-0 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-6">
      <div className="min-w-0">
        <Link to={to} className="block truncate font-medium underline-offset-4 hover:underline">
          {showFreelancer ? (proposal.freelancerName ?? `Freelancer ${proposal.freelancerId}`) : (proposal.projectTitle ?? `Project ${proposal.projectId}`)}
        </Link>
        <p className="text-sm text-mute">
          {showFreelancer && proposal.projectTitle ? `${proposal.projectTitle}, ` : ''}
          sent {timeAgo(proposal.createdAt)}
        </p>
      </div>
      <p className="text-sm">
        <span className="font-semibold">{formatInr(proposal.priceInr)}</span>
        <span className="text-mute"> in {proposal.durationDays} days</span>
      </p>
      <div className="flex items-center gap-3">
        <StatusBadge status={proposal.status} />
        {trailing}
      </div>
    </li>
  );
}
