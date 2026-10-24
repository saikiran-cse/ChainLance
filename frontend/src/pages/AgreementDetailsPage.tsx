import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Info } from 'lucide-react';
import { EscrowDetails, FinalizationStatus, MilestoneList, SnapshotView } from '../components/agreements/AgreementParts';
import { CounterForm } from '../components/negotiation/CounterForm';
import { Async } from '../components/ui/Async';
import { Button } from '../components/ui/Button';
import { DefinitionRow, PageHeader, Panel } from '../components/ui/Panel';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { useToast } from '../hooks/useToast';
import { agreementsService } from '../services/agreements.service';
import type { Agreement } from '../types/marketplace';
import { messageOf } from '../utils/errors';
import { formatDate, formatInr, weiToEth } from '../utils/format';

const STATUS_COPY: Record<string, string> = {
  DRAFT: 'Terms are still being agreed. The agreement is not final.',
  FINALIZED: 'Both parties have finalized and the terms are frozen.',
  FUNDED: 'The escrow contract is funded, as verified by the backend.',
  COMPLETED: 'All milestones are closed.',
  CANCELLED: 'This agreement was cancelled.',
};

function FinalizeBox({ agreement, onChanged }: { agreement: Agreement; onChanged(): void }) {
  const { user } = useAuth();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  const isClient = user?.id === agreement.clientId;
  const isFreelancer = user?.id === agreement.freelancerId;
  const mineDone = isClient ? agreement.clientFinalizedAt : isFreelancer ? agreement.freelancerFinalizedAt : null;

  if (agreement.status !== 'DRAFT') return null;

  async function run() {
    setBusy(true);
    try {
      // The UI shows whatever status the response carries. It never assumes the agreement became final.
      await agreementsService.finalize(agreement.id);
      toast.push({ tone: 'success', message: 'Your finalization was sent. The status below is what the backend reports.' });
      setConfirming(false);
      onChanged();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title="Finalize">
      {mineDone ? (
        <p className="text-mute">You have finalized. The agreement becomes final when the backend confirms the other party has too.</p>
      ) : !isClient && !isFreelancer ? (
        <p className="text-mute">Only the client and the freelancer can finalize.</p>
      ) : confirming ? (
        <div role="group" aria-label="Confirm finalization" className="flex flex-col gap-3">
          <p className="font-medium">Finalize these terms? You are agreeing to the price, deadline and milestones shown here.</p>
          <div className="flex gap-2">
            <Button variant="accent" loading={busy} onClick={() => void run()}>
              Yes, finalize
            </Button>
            <Button variant="ghost" disabled={busy} onClick={() => setConfirming(false)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <Button variant="accent" onClick={() => setConfirming(true)}>
          Finalize my side
        </Button>
      )}
    </Panel>
  );
}

export default function AgreementDetailsPage() {
  const { id } = useParams();
  const agreementId = Number(id);
  const toast = useToast();
  const agreement = useResource(() => agreementsService.get(agreementId), [agreementId]);
  const milestones = useResource(() => agreementsService.milestones(agreementId), [agreementId]);
  useDocumentTitle(agreement.data?.projectTitle ?? 'Agreement');

  function reloadAll() {
    agreement.reload();
    milestones.reload();
  }

  return (
    <div className="flex flex-col gap-8">
      <Link to="/app/agreements" className="inline-flex items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
        <ArrowLeft aria-hidden className="size-4" />
        All agreements
      </Link>
      <Async state={agreement} rows={5}>
        {(a) => {
          const planned = milestones.data && milestones.data.length > 0 ? milestones.data : a.milestones;
          return (
            <>
              <PageHeader title={a.projectTitle ?? `Agreement ${a.id}`} actions={<StatusBadge status={a.status} className="text-sm" />}>
                {STATUS_COPY[a.status] ?? 'Status reported by the backend.'}
              </PageHeader>

              <div className="grid gap-8 lg:grid-cols-[1.6fr_1fr]">
                <div className="flex flex-col gap-8">
                  <Panel title="Agreed terms">
                    <dl>
                      <DefinitionRow term="Client">{a.clientName ?? '—'}</DefinitionRow>
                      <DefinitionRow term="Freelancer">{a.freelancerName ?? '—'}</DefinitionRow>
                      <DefinitionRow term="Scope">
                        {a.projectDescription ? <span className="whitespace-pre-line">{a.projectDescription}</span> : '—'}
                      </DefinitionRow>
                      <DefinitionRow term="Agreed price">
                        {a.priceInr != null ? <span className="font-display text-3xl leading-none">{formatInr(a.priceInr)}</span> : '—'}
                      </DefinitionRow>
                      <DefinitionRow term="Escrow amount">{a.totalWei ? `${weiToEth(a.totalWei)} ETH` : '—'}</DefinitionRow>
                      <DefinitionRow term="Agreed deadline">{formatDate(a.deadline)}</DefinitionRow>
                    </dl>
                  </Panel>

                  <Panel title="Milestones">
                    <Async state={milestones} rows={2}>{() => <MilestoneList milestones={planned} />}</Async>
                  </Panel>

                  <Panel title="Snapshot">
                    <SnapshotView agreement={a} />
                  </Panel>

                  <Panel title="Escrow">
                    <EscrowDetails agreement={a} />
                    <p className="mt-4 flex items-start gap-2 text-sm text-mute">
                      <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
                      Funding and payouts need on-chain transactions. Those screens arrive with the escrow endpoints.
                    </p>
                  </Panel>
                </div>

                <aside className="flex flex-col gap-8 lg:sticky lg:top-8 lg:self-start">
                  <Panel title="Sign-off">
                    <FinalizationStatus agreement={a} />
                  </Panel>
                  <FinalizeBox agreement={a} onChanged={reloadAll} />
                  {a.status === 'DRAFT' && (
                    <Panel title="Change the terms">
                      <p className="mb-4 text-sm text-mute">Propose different terms before anyone finalizes.</p>
                      <CounterForm
                        submitLabel="Send counter"
                        onSend={async (body) => {
                          await agreementsService.counter(a.id, body);
                          toast.push({ tone: 'success', message: 'Counter sent.' });
                          reloadAll();
                        }}
                      />
                    </Panel>
                  )}
                </aside>
              </div>
            </>
          );
        }}
      </Async>
    </div>
  );
}
