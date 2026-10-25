import { useEffect, useState } from 'react';
import { CheckCircle2, Gavel, Loader2, RefreshCw } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { PageHeader, Panel } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { useChainTx } from '../hooks/useChainTx';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useToast } from '../hooks/useToast';
import {
  workflowApi,
  type DisputeDetail,
  type DisputeSummary,
} from '../services/workflow.service';
import { formatDateTime, shortAddress, weiToEth } from '../utils/format';

export default function AdminDisputesPage() {
  useDocumentTitle('Dispute Resolution Portal');
  const toast = useToast();
  const { isPending, resolveDispute } = useChainTx();

  const [disputes, setDisputes] = useState<DisputeSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDisputeId, setSelectedDisputeId] = useState<number | null>(null);
  const [selectedDispute, setSelectedDispute] = useState<DisputeDetail | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('OPEN');

  // Decision form state
  const [decisionType, setDecisionType] = useState<'FREELANCER_FULL' | 'CLIENT_FULL' | 'SPLIT'>('SPLIT');
  const [splitFreelancerEth, setSplitFreelancerEth] = useState('');
  const [decisionExplanation, setDecisionExplanation] = useState('');
  const [submittingDecision, setSubmittingDecision] = useState(false);

  // Escrow address for resolution tx
  const [escrowAddressInput, setEscrowAddressInput] = useState('');

  const loadDisputes = async () => {
    setLoading(true);
    try {
      const data = await workflowApi.adminListDisputes(statusFilter || undefined);
      setDisputes(data.content ?? []);
      if (data.content && data.content.length > 0 && !selectedDisputeId) {
        setSelectedDisputeId(data.content[0].id);
      }
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Failed to load disputes', tone: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDisputes();
  }, [statusFilter]);

  useEffect(() => {
    if (!selectedDisputeId) {
      setSelectedDispute(null);
      return;
    }
    workflowApi
      .getDispute(selectedDisputeId)
      .then((d) => {
        setSelectedDispute(d);
        if (d.adminFreelancerWei) {
          setSplitFreelancerEth(weiToEth(d.adminFreelancerWei));
        }
      })
      .catch((err) => {
        toast.push({ message: err instanceof Error ? err.message : 'Failed to load dispute details', tone: 'error' });
      });
  }, [selectedDisputeId]);

  const handleRecordDecision = async () => {
    if (!selectedDispute) return;
    if (!decisionExplanation.trim()) {
      toast.push({ message: 'Please provide an explanation for the decision', tone: 'error' });
      return;
    }

    setSubmittingDecision(true);
    try {
      let fWei: string | undefined;
      let cWei: string | undefined;

      // Milestone total wei
      const totalWei = BigInt(
        disputes.find((d) => d.id === selectedDispute.id)?.milestoneAmountWei ?? '0'
      );

      if (decisionType === 'SPLIT') {
        const fEth = Number(splitFreelancerEth);
        if (isNaN(fEth) || fEth < 0) {
          toast.push({ message: 'Please enter a valid freelancer ETH amount', tone: 'error' });
          setSubmittingDecision(false);
          return;
        }
        fWei = (BigInt(Math.round(fEth * 1e6)) * 10n ** 12n).toString();
        cWei = (totalWei - BigInt(fWei)).toString();
      }

      const updated = await workflowApi.adminRecordDecision(
        selectedDispute.id,
        decisionType,
        decisionExplanation.trim(),
        fWei,
        cWei
      );
      setSelectedDispute(updated);
      toast.push({ message: 'Decision recorded successfully', tone: 'success' });
      loadDisputes();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Failed to record decision', tone: 'error' });
    } finally {
      setSubmittingDecision(false);
    }
  };

  const handleExecuteResolutionTx = async () => {
    if (!selectedDispute) return;
    const escrowAddr = escrowAddressInput.trim();
    if (!escrowAddr) {
      toast.push({ message: 'Please enter or confirm the Escrow Contract Address', tone: 'error' });
      return;
    }
    if (!selectedDispute.adminFreelancerWei || !selectedDispute.adminClientWei) {
      toast.push({ message: 'Admin decision allocations are missing', tone: 'error' });
      return;
    }

    try {
      toast.push({ message: 'Signing resolution in MetaMask...', tone: 'info' });
      const { txHash } = await resolveDispute(
        escrowAddr,
        selectedDispute.milestoneId,
        selectedDispute.adminFreelancerWei,
        selectedDispute.adminClientWei,
        selectedDispute.adminExplanation ?? 'Admin resolution'
      );
      toast.push({ message: 'Transaction confirmed on-chain. Updating dispute status...', tone: 'info' });
      const updated = await workflowApi.adminSubmitResolutionTx(selectedDispute.id, txHash);
      setSelectedDispute(updated);
      toast.push({ message: 'Dispute resolved and milestone funds distributed!', tone: 'success' });
      loadDisputes();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Resolution failed', tone: 'error' });
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Admin Dispute Resolution Portal"
        actions={
          <Button variant="outline" size="sm" onClick={loadDisputes} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
          </Button>
        }
      >
        Review disputes, inspect evidence submitted by client and freelancer, record legal decisions, and execute on-chain escrow settlements.
      </PageHeader>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2 border-b border-line pb-2">
        {['OPEN', 'DECIDED', 'RESOLVED', ''].map((st) => (
          <button
            key={st || 'ALL'}
            onClick={() => setStatusFilter(st)}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold transition-colors ${
              statusFilter === st
                ? 'bg-ink text-paper'
                : 'text-mute hover:bg-paper hover:text-ink'
            }`}
          >
            {st ? st : 'ALL DISPUTES'}
          </button>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_1.6fr]">
        {/* Left: Disputes List */}
        <div className="flex flex-col gap-3">
          {loading ? (
            <div className="flex h-40 items-center justify-center">
              <Loader2 className="size-6 animate-spin text-mute" />
            </div>
          ) : disputes.length === 0 ? (
            <EmptyState title="No disputes found">
              There are currently no disputes in {statusFilter || 'any'} status.
            </EmptyState>
          ) : (
            disputes.map((d) => {
              const isSelected = selectedDisputeId === d.id;
              return (
                <div
                  key={d.id}
                  onClick={() => setSelectedDisputeId(d.id)}
                  className={`cursor-pointer rounded-lg border p-4 transition-all ${
                    isSelected
                      ? 'border-accent bg-accent/5 ring-1 ring-accent'
                      : 'border-line bg-paper hover:border-mute'
                  }`}
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-ink">Dispute #{d.id}</span>
                    <span
                      className={`rounded px-2 py-0.5 font-semibold ${
                        d.status === 'OPEN'
                          ? 'bg-brick/10 text-brick'
                          : d.status === 'DECIDED'
                          ? 'bg-amber-500/10 text-amber-700'
                          : 'bg-emerald-500/10 text-emerald-700'
                      }`}
                    >
                      {d.status}
                    </span>
                  </div>
                  <p className="mt-2 line-clamp-2 text-sm text-ink">{d.reason}</p>
                  <div className="mt-2 flex items-center justify-between text-xs text-mute">
                    <span>Agreement #{d.agreementId}</span>
                    <span>{weiToEth(d.milestoneAmountWei)} ETH</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Right: Dispute Detail & Decision Workbench */}
        <div>
          {selectedDispute ? (
            <div className="flex flex-col gap-6">
              <Panel title={`Dispute #${selectedDispute.id} Details`}>
                <div className="flex flex-col gap-4 text-sm">
                  <div>
                    <span className="text-xs text-mute">Opened by</span>
                    <p className="font-medium text-ink">{selectedDispute.openedBy?.displayName ?? 'Participant'}</p>
                  </div>
                  <div>
                    <span className="text-xs text-mute">Claim / Reason</span>
                    <p className="rounded-md bg-paper/60 p-3 text-ink">{selectedDispute.reason}</p>
                  </div>

                  {/* Evidence & Reply Thread */}
                  <div>
                    <span className="text-xs font-semibold text-mute">Evidence & Rebuttals ({selectedDispute.responses?.length ?? 0})</span>
                    <div className="mt-2 flex flex-col gap-2">
                      {selectedDispute.responses?.length === 0 ? (
                        <p className="text-xs italic text-mute">No rebuttals submitted yet.</p>
                      ) : (
                        selectedDispute.responses.map((r) => (
                          <div key={r.id} className="rounded border border-line bg-paper p-3 text-xs">
                            <div className="flex items-center justify-between">
                              <span className="font-semibold">{r.user?.displayName ?? 'Participant'}</span>
                              <span className="text-mute">{formatDateTime(r.createdAt)}</span>
                            </div>
                            <p className="mt-1 text-ink">{r.response}</p>
                            {r.evidenceLinks && (
                              <p className="mt-1 text-mute underline">
                                <a href={r.evidenceLinks} target="_blank" rel="noreferrer">
                                  {r.evidenceLinks}
                                </a>
                              </p>
                            )}
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>
              </Panel>

              {/* Action Panel: Decision & Resolution */}
              <Panel title="Admin Resolution Action">
                {selectedDispute.status === 'OPEN' && (
                  <div className="flex flex-col gap-4">
                    <p className="text-xs text-mute">
                      Choose an allocation decision based on the evidence. Once recorded, the dispute advances to DECIDED and is ready for on-chain resolution execution.
                    </p>

                    <div className="grid grid-cols-3 gap-3">
                      {(['FREELANCER_FULL', 'CLIENT_FULL', 'SPLIT'] as const).map((t) => (
                        <button
                          key={t}
                          type="button"
                          onClick={() => setDecisionType(t)}
                          className={`rounded-md border p-3 text-xs font-semibold transition-all ${
                            decisionType === t
                              ? 'border-ink bg-ink text-paper'
                              : 'border-line bg-paper text-ink hover:bg-paper/70'
                          }`}
                        >
                          {t === 'FREELANCER_FULL'
                            ? '100% Freelancer'
                            : t === 'CLIENT_FULL'
                            ? '100% Refund Client'
                            : 'Custom Split'}
                        </button>
                      ))}
                    </div>

                    {decisionType === 'SPLIT' && (
                      <div>
                        <label className="text-xs font-medium text-mute">Freelancer Share (ETH)</label>
                        <input
                          type="text"
                          value={splitFreelancerEth}
                          onChange={(e) => setSplitFreelancerEth(e.target.value)}
                          placeholder="e.g. 0.1"
                          className="mt-1 w-full rounded border border-line bg-paper p-2 text-sm"
                        />
                      </div>
                    )}

                    <div>
                      <label className="text-xs font-medium text-mute">Admin Decision Explanation</label>
                      <textarea
                        rows={3}
                        value={decisionExplanation}
                        onChange={(e) => setDecisionExplanation(e.target.value)}
                        placeholder="State legal and technical reasons for this split decision..."
                        className="mt-1 w-full rounded border border-line bg-paper p-2.5 text-sm"
                      />
                    </div>

                    <Button
                      onClick={handleRecordDecision}
                      disabled={submittingDecision || !decisionExplanation.trim()}
                      size="sm"
                    >
                      {submittingDecision ? 'Recording...' : 'Record Admin Decision'}
                    </Button>
                  </div>
                )}

                {selectedDispute.status === 'DECIDED' && (
                  <div className="flex flex-col gap-4">
                    <div className="rounded-md bg-amber-500/10 p-3 text-xs text-amber-900 dark:text-amber-200">
                      <p className="font-semibold">Recorded Decision: {selectedDispute.adminDecision}</p>
                      <p className="mt-1">
                        Freelancer: {selectedDispute.adminFreelancerWei ? `${weiToEth(selectedDispute.adminFreelancerWei)} ETH` : '0'} | Client Refund: {selectedDispute.adminClientWei ? `${weiToEth(selectedDispute.adminClientWei)} ETH` : '0'}
                      </p>
                      {selectedDispute.adminExplanation && <p className="mt-1 italic">"{selectedDispute.adminExplanation}"</p>}
                    </div>

                    <div>
                      <label className="text-xs font-medium text-mute">Target Escrow Contract Address</label>
                      <input
                        type="text"
                        value={escrowAddressInput}
                        onChange={(e) => setEscrowAddressInput(e.target.value)}
                        placeholder="0x..."
                        className="mt-1 w-full rounded border border-line bg-paper p-2 text-sm font-mono"
                      />
                    </div>

                    <Button
                      onClick={handleExecuteResolutionTx}
                      disabled={isPending || !escrowAddressInput.trim()}
                      size="sm"
                    >
                      <Gavel className="size-4" />
                      {isPending ? 'Executing On-chain...' : 'Execute On-chain Resolution (MetaMask)'}
                    </Button>
                  </div>
                )}

                {selectedDispute.status === 'RESOLVED' && (
                  <div className="rounded-md bg-emerald-500/10 p-4 text-xs text-emerald-800 dark:text-emerald-200">
                    <div className="flex items-center gap-2 font-semibold">
                      <CheckCircle2 className="size-4" /> Dispute Resolved & Closed
                    </div>
                    {selectedDispute.resolvedTxHash && (
                      <p className="mt-1 font-mono text-mute">Tx: {shortAddress(selectedDispute.resolvedTxHash)}</p>
                    )}
                  </div>
                )}
              </Panel>
            </div>
          ) : (
            <div className="rounded-lg border border-line bg-paper p-8 text-center text-sm text-mute">
              Select a dispute from the list to view evidence and take action.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
