import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ExternalLink } from 'lucide-react';
import { Async } from '../components/ui/Async';
import { Button, ButtonLink } from '../components/ui/Button';
import { PageHeader, Panel } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { useToast } from '../hooks/useToast';
import { projectsService } from '../services/projects.service';
import { proposalsService } from '../services/proposals.service';
import type { Proposal } from '../types/marketplace';
import { messageOf } from '../utils/errors';
import { formatInr, timeAgo } from '../utils/format';
import { isHttpUrl } from '../utils/validation';

type Action = 'shortlist' | 'accept' | 'reject';

const CONFIRM: Record<Action, { label: string; question: string; done: string }> = {
  shortlist: { label: 'Shortlist', question: 'Shortlist this proposal and open a negotiation?', done: 'Proposal shortlisted.' },
  accept: { label: 'Accept', question: 'Accept this proposal?', done: 'Proposal accepted.' },
  reject: { label: 'Reject', question: 'Reject this proposal? The freelancer will see it.', done: 'Proposal rejected.' },
};

function ProposalCard({ proposal, onChanged }: { proposal: Proposal; onChanged(): void }) {
  const toast = useToast();
  const [confirming, setConfirming] = useState<Action | null>(null);
  const [busy, setBusy] = useState(false);
  const decided = proposal.status === 'ACCEPTED' || proposal.status === 'REJECTED' || proposal.status === 'WITHDRAWN';

  async function run(action: Action) {
    setBusy(true);
    try {
      await proposalsService[action](proposal.id);
      toast.push({ tone: 'success', message: CONFIRM[action].done });
      setConfirming(null);
      onChanged();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="rounded-lg border border-line bg-paper-raised p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h3 className="font-display text-3xl leading-tight">{proposal.freelancerName ?? `Freelancer ${proposal.freelancerId}`}</h3>
          <p className="text-sm text-mute">Sent {timeAgo(proposal.createdAt)}</p>
        </div>
        <StatusBadge status={proposal.status} />
      </div>
      <dl className="mt-5 flex flex-wrap gap-x-10 gap-y-3">
        <div>
          <dt className="text-sm text-mute">Price</dt>
          <dd className="font-display text-4xl leading-none">{formatInr(proposal.priceInr)}</dd>
        </div>
        <div>
          <dt className="text-sm text-mute">Duration</dt>
          <dd className="font-display text-4xl leading-none">{proposal.durationDays} days</dd>
        </div>
      </dl>
      <p className="mt-5 max-w-2xl whitespace-pre-line text-ink-soft">{proposal.message}</p>
      {proposal.portfolioLinks.length > 0 && (
        <ul className="mt-4 flex flex-col gap-1.5 text-sm" aria-label="Portfolio links">
          {proposal.portfolioLinks.map((link) => (
            <li key={link} className="min-w-0 truncate">
              {isHttpUrl(link) ? (
                <a
                  href={link}
                  target="_blank"
                  rel="noopener noreferrer nofollow"
                  className="inline-flex items-center gap-1.5 underline underline-offset-4 hover:text-mute"
                >
                  {link}
                  <ExternalLink aria-hidden className="size-3.5 shrink-0" />
                </a>
              ) : (
                <span className="text-mute">{link}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-6 border-t border-line pt-5">
        {confirming ? (
          <div role="group" aria-label="Confirm action" className="flex flex-wrap items-center gap-3">
            <p className="font-medium">{CONFIRM[confirming].question}</p>
            <Button size="sm" variant={confirming === 'reject' ? 'danger' : 'primary'} loading={busy} onClick={() => void run(confirming)}>
              Yes, {CONFIRM[confirming].label.toLowerCase()}
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirming(null)}>
              Cancel
            </Button>
          </div>
        ) : (
          <div className="flex flex-wrap gap-3">
            {proposal.status === 'SUBMITTED' && (
              <Button variant="primary" onClick={() => setConfirming('shortlist')}>
                Shortlist
              </Button>
            )}
            {(proposal.status === 'SUBMITTED' || proposal.status === 'SHORTLISTED') && (
              <Button variant="accent" onClick={() => setConfirming('accept')}>
                Accept
              </Button>
            )}
            {!decided && (
              <Button variant="danger" onClick={() => setConfirming('reject')}>
                Reject
              </Button>
            )}
            {(proposal.status === 'SHORTLISTED' || proposal.status === 'ACCEPTED') && (
              <ButtonLink to={`/app/proposals/${proposal.id}/negotiation`} variant="outline">
                Open negotiation
              </ButtonLink>
            )}
          </div>
        )}
      </div>
    </article>
  );
}

export default function ProposalReviewPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const project = useResource(() => projectsService.get(projectId), [projectId]);
  const proposals = useResource(() => proposalsService.forProject(projectId), [projectId]);
  useDocumentTitle(project.data ? `Proposals for ${project.data.title}` : 'Proposals');

  return (
    <div className="flex flex-col gap-8">
      <Link to="/app/projects" className="inline-flex items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
        <ArrowLeft aria-hidden className="size-4" />
        My projects
      </Link>
      <PageHeader title="Proposals">
        {project.data ? `For “${project.data.title}”, budget ${formatInr(project.data.budgetInr)}.` : 'Review what freelancers have sent.'}
      </PageHeader>
      <Async state={proposals} rows={3}>
        {(items) =>
          items.length === 0 ? (
            <EmptyState title="No proposals yet">
              Freelancers will appear here once they respond. A clear description and a realistic budget help.
            </EmptyState>
          ) : (
            <div className="flex flex-col gap-6">
              <Panel padded={false} className="border-dashed bg-transparent">
                <p className="px-5 py-3 text-sm text-mute">
                  {items.length} {items.length === 1 ? 'proposal' : 'proposals'}. Accepting a proposal is a request to the
                  backend: the agreement exists only once the backend says so.
                </p>
              </Panel>
              {items.map((p) => (
                <ProposalCard key={p.id} proposal={p} onChanged={proposals.reload} />
              ))}
            </div>
          )
        }
      </Async>
    </div>
  );
}
