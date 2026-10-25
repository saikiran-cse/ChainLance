import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Clock } from 'lucide-react';
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
  const navigate = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [rejectConfirm, setRejectConfirm] = useState(false);
  const { proposal, agreement } = thread;
  const isClient = user?.role === 'CLIENT';
  const open = proposal.status === 'SUBMITTED' || proposal.status === 'SHORTLISTED';

  const offers = proposal.offers ?? [];
  const pendingOffer = offers.find((o) => o.status === 'PENDING');
  const acceptedOffer = offers.find((o) => o.status === 'ACCEPTED');
  const termsAgreed = !pendingOffer && !!acceptedOffer;

  async function handleAcceptOffer(offerId: number) {
    setBusy(true);
    try {
      await negotiationService.acceptOffer(proposal.id, offerId);
      toast.push({ tone: 'success', message: 'Offer accepted! Terms are agreed.' });
      onChanged();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(false);
    }
  }

  async function handleWithdrawOffer(offerId: number) {
    setBusy(true);
    try {
      await negotiationService.withdrawOffer(proposal.id, offerId);
      toast.push({ tone: 'success', message: 'Offer withdrawn.' });
      onChanged();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(false);
    }
  }

  async function handleAcceptProposal() {
    setBusy(true);
    try {
      const ag = await proposalsService.accept(proposal.id);
      toast.push({ tone: 'success', message: 'Agreement created & snapshot frozen!' });
      navigate(`/app/agreements/${ag.id}`);
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(false);
    }
  }

  async function handleRejectProposal() {
    setBusy(true);
    try {
      await proposalsService.reject(proposal.id);
      toast.push({ tone: 'success', message: 'Proposal rejected.' });
      setRejectConfirm(false);
      onChanged();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(false);
    }
  }

  async function handleWithdrawProposal() {
    if (!window.confirm('Withdraw this proposal?')) return;
    setBusy(true);
    try {
      await proposalsService.withdraw(proposal.id);
      toast.push({ tone: 'success', message: 'Proposal withdrawn.' });
      onChanged();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {agreement ? (
        <div>
          <p className="text-sm text-mute">Agreement</p>
          <div className="mt-1 flex items-center gap-3">
            <StatusBadge status={agreement.status} />
            <span className="text-sm text-mute">Snapshot frozen</span>
          </div>
          <ButtonLink to={`/app/agreements/${agreement.id}`} variant="accent" className="mt-4 w-full">
            Open agreement
          </ButtonLink>
        </div>
      ) : open ? (
        <div className="flex flex-col gap-4">
          {pendingOffer && (
            <div className="rounded-lg border border-ochre/40 bg-ochre/5 p-4">
              <div className="flex items-center gap-2 text-xs font-semibold text-ochre">
                <Clock className="size-4" />
                <span>PENDING OFFER: {formatInr(pendingOffer.priceInr)} ({pendingOffer.durationDays} days)</span>
              </div>
              <p className="mt-1 text-xs text-mute">
                Offered by {pendingOffer.offeredBy === user?.id ? 'you' : 'the other party'}.
              </p>
              <div className="mt-3 flex gap-2">
                {pendingOffer.offeredBy !== user?.id ? (
                  <Button
                    size="sm"
                    variant="accent"
                    loading={busy}
                    onClick={() => void handleAcceptOffer(pendingOffer.id)}
                  >
                    Accept this price
                  </Button>
                ) : (
                  <Button
                    size="sm"
                    variant="outline"
                    loading={busy}
                    onClick={() => void handleWithdrawOffer(pendingOffer.id)}
                  >
                    Withdraw offer
                  </Button>
                )}
              </div>
            </div>
          )}

          {termsAgreed && (
            <div className="rounded-lg border border-lime/40 bg-lime/10 p-4">
              <div className="flex items-center gap-2 text-xs font-semibold text-ink">
                <CheckCircle2 className="size-4 text-ink" />
                <span>TERMS AGREED: {formatInr(acceptedOffer.priceInr)} ({acceptedOffer.durationDays} days)</span>
              </div>
              <p className="mt-1 text-xs text-mute">
                {isClient
                  ? 'Both sides have agreed. Click below to freeze the agreement snapshot.'
                  : 'Terms agreed. Waiting for client to create the agreement.'}
              </p>
              {isClient && (
                <div className="mt-3">
                  <Button
                    variant="accent"
                    loading={busy}
                    onClick={() => void handleAcceptProposal()}
                  >
                    Accept proposal & freeze agreement
                  </Button>
                </div>
              )}
            </div>
          )}

          {isClient && !termsAgreed && !pendingOffer && (
            <p className="text-sm text-mute">
              Terms must be agreed before accepting the proposal. Make or accept an offer.
            </p>
          )}

          <div className="border-t border-line pt-3 flex flex-wrap gap-2">
            {isClient && (
              rejectConfirm ? (
                <div className="flex items-center gap-2">
                  <Button size="sm" variant="danger" loading={busy} onClick={() => void handleRejectProposal()}>
                    Confirm reject
                  </Button>
                  <Button size="sm" variant="ghost" disabled={busy} onClick={() => setRejectConfirm(false)}>
                    Cancel
                  </Button>
                </div>
              ) : (
                <Button size="sm" variant="ghost" className="text-brick hover:bg-brick/10" onClick={() => setRejectConfirm(true)}>
                  Reject proposal
                </Button>
              )
            )}
            {!isClient && (
              <Button size="sm" variant="ghost" className="text-brick hover:bg-brick/10" loading={busy} onClick={() => void handleWithdrawProposal()}>
                Withdraw proposal
              </Button>
            )}
          </div>
        </div>
      ) : (
        <p className="text-sm text-mute">
          {proposal.status === 'ACCEPTED'
            ? 'The proposal was accepted.'
            : 'This proposal is closed.'}
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
                        toast.push({ tone: 'success', message: body.terms ? 'Offer sent.' : 'Message sent.' });
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
