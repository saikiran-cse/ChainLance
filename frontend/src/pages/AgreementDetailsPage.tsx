import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Info } from 'lucide-react';
import { EscrowDetails, FinalizationStatus, MilestoneList, SnapshotView } from '../components/agreements/AgreementParts';
import { Async } from '../components/ui/Async';
import { DefinitionRow, PageHeader, Panel } from '../components/ui/Panel';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { agreementsService } from '../services/agreements.service';
import { formatDate, formatInr, weiToEth } from '../utils/format';

const STATUS_COPY: Record<string, string> = {
  DRAFT: 'Terms are still being agreed. The agreement is not final.',
  FINALIZED: 'Both parties agreed to terms; snapshot is frozen.',
  FUNDED: 'The escrow contract is funded, as verified by the backend.',
  COMPLETED: 'All milestones are closed.',
  CANCELLED: 'This agreement was cancelled.',
};

export default function AgreementDetailsPage() {
  const { id } = useParams();
  const agreementId = Number(id);
  const agreement = useResource(() => agreementsService.get(agreementId), [agreementId]);
  const milestones = useResource(() => agreementsService.milestones(agreementId), [agreementId]);
  useDocumentTitle(agreement.data?.projectTitle ?? 'Agreement');

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
                </aside>
              </div>
            </>
          );
        }}
      </Async>
    </div>
  );
}
