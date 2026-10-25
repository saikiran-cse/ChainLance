import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Star } from 'lucide-react';
import { FinalizationStatus, SnapshotView } from '../components/agreements/AgreementParts';
import { EscrowManager } from '../components/workflow/EscrowManager';
import { MilestoneWorkflow } from '../components/workflow/MilestoneWorkflow';
import { ReviewModal } from '../components/workflow/ReviewModal';
import { Async } from '../components/ui/Async';
import { Button } from '../components/ui/Button';
import { DefinitionRow, PageHeader, Panel } from '../components/ui/Panel';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { agreementsService } from '../services/agreements.service';
import { workflowApi } from '../services/workflow.service';
import { formatDate, formatInr, weiToEth } from '../utils/format';

const STATUS_COPY: Record<string, string> = {
  DRAFT: 'Terms are still being agreed. The agreement is not final.',
  FINALIZED: 'Both parties agreed to terms; snapshot is frozen. Ready to deploy escrow.',
  FUNDED: 'The escrow contract is funded, as verified by the backend. Work in progress.',
  COMPLETED: 'All milestones are resolved and closed.',
  CANCELLED: 'This agreement was cancelled.',
};

export default function AgreementDetailsPage() {
  const { id } = useParams();
  const agreementId = Number(id);

  const agreement = useResource(() => agreementsService.get(agreementId), [agreementId]);
  const milestones = useResource(() => agreementsService.milestones(agreementId), [agreementId]);
  const chainConfig = useResource(() => workflowApi.getChainConfig(), []);

  const [reviewOpen, setReviewOpen] = useState(false);
  useDocumentTitle(agreement.data?.projectTitle ?? 'Agreement');

  const handleRefresh = () => {
    agreement.reload();
    milestones.reload();
  };

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
              <PageHeader
                title={a.projectTitle ?? `Agreement ${a.id}`}
                actions={
                  <div className="flex items-center gap-3">
                    <StatusBadge status={a.status} className="text-sm" />
                    {a.status === 'COMPLETED' && (
                      <Button size="sm" onClick={() => setReviewOpen(true)}>
                        <Star className="size-4" /> Leave Review
                      </Button>
                    )}
                  </div>
                }
              >
                {STATUS_COPY[a.status] ?? 'Status reported by the backend.'}
              </PageHeader>

              <div className="grid gap-8 lg:grid-cols-[1.6fr_1fr]">
                <div className="flex flex-col gap-8">
                  {/* Escrow Deployment & Funding Manager */}
                  <EscrowManager
                    agreement={a}
                    milestones={planned}
                    chainConfig={chainConfig.data ?? null}
                    onRefresh={handleRefresh}
                  />

                  {/* Milestones & Execution Workflow */}
                  <Panel title="Milestones & Deliverables">
                    <MilestoneWorkflow
                      agreement={a}
                      milestones={planned}
                      onRefresh={handleRefresh}
                    />
                  </Panel>

                  {/* Agreed Terms */}
                  <Panel title="Agreed Terms">
                    <dl>
                      <DefinitionRow term="Client">{a.clientName ?? '—'}</DefinitionRow>
                      <DefinitionRow term="Freelancer">{a.freelancerName ?? '—'}</DefinitionRow>
                      <DefinitionRow term="Scope">
                        {a.projectDescription ? <span className="whitespace-pre-line">{a.projectDescription}</span> : '—'}
                      </DefinitionRow>
                      <DefinitionRow term="Agreed Price">
                        {a.priceInr != null ? <span className="font-display text-3xl leading-none">{formatInr(a.priceInr)}</span> : '—'}
                      </DefinitionRow>
                      <DefinitionRow term="Escrow Amount">{a.totalWei ? `${weiToEth(a.totalWei)} ETH` : '—'}</DefinitionRow>
                      <DefinitionRow term="Agreed Deadline">{formatDate(a.deadline)}</DefinitionRow>
                    </dl>
                  </Panel>

                  {/* Frozen Snapshot */}
                  <Panel title="Snapshot">
                    <SnapshotView agreement={a} />
                  </Panel>
                </div>

                <aside className="flex flex-col gap-8 lg:sticky lg:top-8 lg:self-start">
                  <Panel title="Sign-off & Parties">
                    <FinalizationStatus agreement={a} />
                  </Panel>
                </aside>
              </div>

              {/* Review Modal */}
              <ReviewModal
                agreementId={agreementId}
                isOpen={reviewOpen}
                onClose={() => setReviewOpen(false)}
                onSubmitted={handleRefresh}
              />
            </>
          );
        }}
      </Async>
    </div>
  );
}
