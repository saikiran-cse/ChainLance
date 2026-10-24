import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { CounterForm } from '../components/negotiation/CounterForm';
import { OfferThread } from '../components/negotiation/OfferThread';
import { Async } from '../components/ui/Async';
import { Button, ButtonLink } from '../components/ui/Button';
import { PageHeader, Panel } from '../components/ui/Panel';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { useToast } from '../hooks/useToast';
import { negotiationService } from '../services/negotiation.service';
import { proposalsService } from '../services/proposals.service';
import type { NegotiationThread } from '../types/marketplace';
import { messageOf } from '../utils/errors';
import { formatInr } from '../utils/format';

function Actions({ thread, onChanged }: { thread: NegotiationThread; onChanged(): void }) {
  const { user } = useAuth();
  const toast = useToast();
  const [busy, setBusy] = useState<'accept' | 'reject' | null>(null);
  const [confirm, setConfirm] = useState<'accept' | 'reject' | null>(null);
  const { proposal, agreement } = thread;
  const isClient = user?.role === 'CLIENT';
  const open = proposal.status === 'SUBMITTED' || proposal.status === 'SHORTLISTED';

  async function run(action: 'accept' | 'reject') {
    setBusy(action);
    try {
      await proposalsService[action](proposal.id);
      toast.push({ tone: 'success', message: action === 'accept' ? 'Proposal accepted.' : 'Proposal rejected.' });
      setConfirm(null);
      onChanged();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {agreement ? (
        <div>
          <p className="text-sm text-mute">Agreement</p>
          <div className="mt-1 flex items-center gap-3">
            <StatusBadge status={agreement.status} />
            <span className="text-sm text-mute">as reported by the backend</span>
          </div>
          <ButtonLink to={`/app/agreements/${agreement.id}`} variant="accent" className="mt-4 w-full">
            Open agreement
          </ButtonLink>
        </div>
      ) : isClient && open ? (
        confirm ? (
          <div role="group" aria-label="Confirm" className="flex flex-col gap-3">
            <p className="font-medium">{confirm === 'accept' ? 'Accept the current terms?' : 'Reject this proposal?'}</p>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant={confirm === 'reject' ? 'danger' : 'primary'}
                loading={busy === confirm}
                onClick={() => void run(confirm)}
              >
                Yes, {confirm}
              </Button>
              <Button size="sm" variant="ghost" disabled={!!busy} onClick={() => setConfirm(null)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <Button variant="accent" onClick={() => setConfirm('accept')}>
              Accept current terms
            </Button>
            <Button variant="danger" onClick={() => setConfirm('reject')}>
              Reject proposal
            </Button>
          </div>
        )
      ) : (
        <p className="text-sm text-mute">
          {proposal.status === 'ACCEPTED'
            ? 'The proposal is accepted. The agreement appears here once the backend creates it.'
            : open
              ? 'The client accepts or rejects. Until then, keep negotiating in the thread.'
              : 'This negotiation is closed.'}
        </p>
      )}
    </div>
  );
}

export default function NegotiationPage() {
  const { id } = useParams();
  const proposalId = Number(id);
  const { user } = useAuth();
  const toast = useToast();
  const thread = useResource(() => negotiationService.thread(proposalId), [proposalId]);
  useDocumentTitle('Negotiation');

  const backTo = user?.role === 'CLIENT' ? '/app' : '/app/proposals';
  const closed = thread.data ? thread.data.proposal.status === 'REJECTED' || thread.data.proposal.status === 'WITHDRAWN' : false;

  return (
    <div className="flex flex-col gap-8">
      <Link to={backTo} className="inline-flex items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
        <ArrowLeft aria-hidden className="size-4" />
        Back
      </Link>
      <Async state={thread} rows={4}>
        {(t) => (
          <>
            <PageHeader title="Negotiation" actions={<StatusBadge status={t.proposal.status} />}>
              {t.proposal.projectTitle ?? `Project ${t.proposal.projectId}`}, with{' '}
              {user?.id === t.proposal.freelancerId ? 'the client' : (t.proposal.freelancerName ?? 'the freelancer')}.
            </PageHeader>
            <div className="grid gap-10 lg:grid-cols-[1.6fr_1fr]">
              <div className="flex flex-col gap-10">
                {user && <OfferThread proposal={t.proposal} messages={t.messages} currentUserId={user.id} />}
                {closed ? (
                  <p className="rounded-md border border-line p-4 text-mute">This negotiation is closed, so no more offers can be sent.</p>
                ) : (
                  <Panel title="Reply or counter">
                    <CounterForm
                      defaults={{ priceInr: t.proposal.priceInr, durationDays: t.proposal.durationDays }}
                      onSend={async (body) => {
                        await negotiationService.send(proposalId, body);
                        toast.push({ tone: 'success', message: body.terms ? 'Counteroffer sent.' : 'Message sent.' });
                        thread.reload();
                      }}
                    />
                  </Panel>
                )}
              </div>
              <aside className="flex flex-col gap-6 lg:sticky lg:top-8 lg:self-start">
                <Panel title="Opening proposal">
                  <dl className="grid gap-3 text-sm">
                    <div>
                      <dt className="text-mute">Price</dt>
                      <dd className="font-display text-3xl leading-none">{formatInr(t.proposal.priceInr)}</dd>
                    </div>
                    <div>
                      <dt className="text-mute">Duration</dt>
                      <dd className="font-display text-3xl leading-none">{t.proposal.durationDays} days</dd>
                    </div>
                  </dl>
                </Panel>
                <Panel title="Decision">
                  <Actions thread={t} onChanged={thread.reload} />
                </Panel>
              </aside>
            </div>
          </>
        )}
      </Async>
    </div>
  );
}
