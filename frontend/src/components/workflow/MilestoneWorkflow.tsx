import { useEffect, useState } from 'react';
import {
  AlertTriangle,
  Calendar,
  ChevronDown,
  ChevronUp,
  Clock,
  FileCheck,
  Loader2,
  Scale,
  Send,
  Upload,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useChainTx } from '../../hooks/useChainTx';
import { useToast } from '../../hooks/useToast';
import {
  workflowApi,
  type DisputeDetail,
  type ExtensionItem,
  type SettlementItem,
  type SubmissionItem,
} from '../../services/workflow.service';
import type { Agreement, AgreementMilestone } from '../../types/marketplace';
import { formatDate, formatDateTime, formatInr, humanizeEnum, weiToEth } from '../../utils/format';
import { Button } from '../ui/Button';
import { StatusBadge } from '../ui/StatusBadge';

interface Props {
  agreement: Agreement;
  milestones: AgreementMilestone[];
  onRefresh: () => void;
}

export function MilestoneWorkflow({ agreement, milestones, onRefresh }: Props) {
  const { user } = useAuth();
  const toast = useToast();
  const { isPending, approveMilestone, proposeSettlement, acceptSettlement, openDispute } = useChainTx();

  const isClient = user?.id === agreement.clientId;
  const isFreelancer = user?.id === agreement.freelancerId;

  // Active modal/form states
  const [selectedMilestone, setSelectedMilestone] = useState<AgreementMilestone | null>(null);
  const [activeModal, setActiveModal] = useState<
    'SUBMIT' | 'REVISION' | 'EXTENSION' | 'SETTLEMENT' | 'DISPUTE' | null
  >(null);

  // Form input states
  const [subDesc, setSubDesc] = useState('');
  const [subLinks, setSubLinks] = useState('');
  const [revReason, setRevReason] = useState('');
  const [extDays, setExtDays] = useState(7);
  const [extReason, setExtReason] = useState('');
  const [settleFreelancerInr, setSettleFreelancerInr] = useState('');
  const [settleReason, setSettleReason] = useState('');
  const [disputeReason, setDisputeReason] = useState('');
  const [disputeReplyText, setDisputeReplyText] = useState('');
  const [disputeEvidence, setDisputeEvidence] = useState('');

  // Milestone sub-resources state
  const [submissions, setSubmissions] = useState<Record<number, SubmissionItem[]>>({});
  const [extensions, setExtensions] = useState<Record<number, ExtensionItem[]>>({});
  const [settlements, setSettlements] = useState<Record<number, SettlementItem[]>>({});
  const [disputes, setDisputes] = useState<Record<number, DisputeDetail[]>>({});
  const [expandedMilestone, setExpandedMilestone] = useState<number | null>(null);

  const loadMilestoneDetails = async (milestoneId: number) => {
    try {
      const [subs, exts, setts, disps] = await Promise.all([
        workflowApi.getSubmissions(milestoneId),
        workflowApi.getExtensions(milestoneId),
        workflowApi.getSettlements(milestoneId),
        workflowApi.getDisputesForMilestone(milestoneId),
      ]);
      setSubmissions((prev) => ({ ...prev, [milestoneId]: subs }));
      setExtensions((prev) => ({ ...prev, [milestoneId]: exts }));
      setSettlements((prev) => ({ ...prev, [milestoneId]: setts }));
      setDisputes((prev) => ({ ...prev, [milestoneId]: disps }));
    } catch {
      // Ignore background load failures
    }
  };

  useEffect(() => {
    milestones.forEach((m) => {
      if (m.id) loadMilestoneDetails(m.id);
    });
  }, [milestones]);

  const toggleExpand = (mId: number) => {
    if (expandedMilestone === mId) {
      setExpandedMilestone(null);
    } else {
      setExpandedMilestone(mId);
      loadMilestoneDetails(mId);
    }
  };

  // Submit Work
  const handleSubmitWork = async () => {
    if (!selectedMilestone?.id) return;
    try {
      await workflowApi.submitWork(selectedMilestone.id, subDesc, subLinks || undefined);
      toast.push({ message: 'Work submitted for client review', tone: 'success' });
      setActiveModal(null);
      setSubDesc('');
      setSubLinks('');
      loadMilestoneDetails(selectedMilestone.id);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Submission failed', tone: 'error' });
    }
  };

  // Request Revision
  const handleRequestRevision = async (submissionId: number) => {
    if (!revReason.trim()) {
      toast.push({ message: 'Please provide a reason for the revision request', tone: 'error' });
      return;
    }
    try {
      await workflowApi.requestRevision(submissionId, revReason.trim());
      toast.push({ message: 'Revision requested', tone: 'success' });
      setActiveModal(null);
      setRevReason('');
      if (selectedMilestone?.id) loadMilestoneDetails(selectedMilestone.id);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Revision request failed', tone: 'error' });
    }
  };

  // Approve Submission & Release Escrow
  const handleApproveSubmission = async (m: AgreementMilestone, subId: number) => {
    try {
      let txHash: string | undefined;
      if (agreement.escrowAddress) {
        toast.push({ message: 'Signing release transaction in MetaMask...', tone: 'info' });
        txHash = await approveMilestone(agreement.escrowAddress, m.position);
        toast.push({ message: 'Transaction confirmed on-chain. Approving milestone...', tone: 'info' });
      }
      await workflowApi.approveSubmission(subId, txHash);
      toast.push({ message: 'Milestone approved and payment released!', tone: 'success' });
      if (m.id) loadMilestoneDetails(m.id);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Approval failed', tone: 'error' });
    }
  };

  // Request Extension
  const handleRequestExtension = async () => {
    if (!selectedMilestone?.id) return;
    try {
      const cur = selectedMilestone.effectiveDeadline ? new Date(selectedMilestone.effectiveDeadline) : new Date();
      const nextDate = new Date(cur.getTime() + extDays * 24 * 60 * 60 * 1000);
      await workflowApi.requestExtension(selectedMilestone.id, nextDate.toISOString(), extReason);
      toast.push({ message: 'Extension request submitted', tone: 'success' });
      setActiveModal(null);
      setExtReason('');
      loadMilestoneDetails(selectedMilestone.id);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Extension request failed', tone: 'error' });
    }
  };

  // Decide Extension
  const handleDecideExtension = async (extId: number, approve: boolean, milestoneId: number) => {
    try {
      if (approve) {
        await workflowApi.approveExtension(extId, 'Approved by client');
        toast.push({ message: 'Deadline extension approved', tone: 'success' });
      } else {
        await workflowApi.rejectExtension(extId, 'Rejected by client');
        toast.push({ message: 'Deadline extension rejected', tone: 'info' });
      }
      loadMilestoneDetails(milestoneId);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Failed to update extension', tone: 'error' });
    }
  };

  // Propose Settlement
  const handleProposeSettlement = async () => {
    if (!selectedMilestone?.id || !selectedMilestone.amountWei || !selectedMilestone.amountInr) return;
    const fInr = Number(settleFreelancerInr);
    if (isNaN(fInr) || fInr < 0 || fInr > selectedMilestone.amountInr) {
      toast.push({ message: 'Please enter a valid freelancer INR amount within milestone budget', tone: 'error' });
      return;
    }

    try {
      const totalWei = BigInt(selectedMilestone.amountWei);
      const fWei = (totalWei * BigInt(Math.round(fInr * 100))) / BigInt(Math.round(selectedMilestone.amountInr * 100));
      const cWei = totalWei - fWei;

      let txHash: string | undefined;
      if (agreement.escrowAddress) {
        toast.push({ message: 'Submitting settlement proposal on-chain in MetaMask...', tone: 'info' });
        txHash = await proposeSettlement(agreement.escrowAddress, selectedMilestone.position, fWei, cWei);
      }
      await workflowApi.proposeSettlement(selectedMilestone.id, fWei.toString(), cWei.toString(), settleReason, txHash);
      toast.push({ message: 'Settlement proposal submitted', tone: 'success' });
      setActiveModal(null);
      setSettleFreelancerInr('');
      setSettleReason('');
      loadMilestoneDetails(selectedMilestone.id);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Settlement proposal failed', tone: 'error' });
    }
  };

  // Accept Settlement
  const handleAcceptSettlement = async (m: AgreementMilestone, sett: SettlementItem) => {
    if (!agreement.escrowAddress) return;
    try {
      toast.push({ message: 'Signing settlement acceptance in MetaMask...', tone: 'info' });
      const txHash = await acceptSettlement(agreement.escrowAddress, m.position, sett.freelancerWei, sett.clientWei);
      toast.push({ message: 'On-chain settlement executed. Updating backend...', tone: 'info' });
      await workflowApi.acceptSettlement(sett.id, txHash);
      toast.push({ message: 'Settlement executed successfully!', tone: 'success' });
      if (m.id) loadMilestoneDetails(m.id);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Failed to accept settlement', tone: 'error' });
    }
  };

  // Open Dispute
  const handleOpenDispute = async () => {
    if (!selectedMilestone?.id) return;
    if (!disputeReason.trim()) {
      toast.push({ message: 'Please provide a reason for the dispute', tone: 'error' });
      return;
    }

    try {
      let txHash: string | undefined;
      if (agreement.escrowAddress) {
        toast.push({ message: 'Opening dispute on-chain in MetaMask...', tone: 'info' });
        const res = await openDispute(agreement.escrowAddress, selectedMilestone.position, disputeReason.trim());
        txHash = res.txHash;
      }
      await workflowApi.openDispute(selectedMilestone.id, disputeReason.trim(), txHash);
      toast.push({ message: 'Dispute opened. Milestone frozen for admin resolution.', tone: 'success' });
      setActiveModal(null);
      setDisputeReason('');
      loadMilestoneDetails(selectedMilestone.id);
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Dispute creation failed', tone: 'error' });
    }
  };

  // Reply to Dispute
  const handleReplyToDispute = async (disputeId: number, milestoneId: number) => {
    if (!disputeReplyText.trim()) return;
    try {
      await workflowApi.replyToDispute(disputeId, disputeReplyText.trim(), disputeEvidence.trim() || undefined);
      toast.push({ message: 'Evidence response added to dispute', tone: 'success' });
      setDisputeReplyText('');
      setDisputeEvidence('');
      loadMilestoneDetails(milestoneId);
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Reply failed', tone: 'error' });
    }
  };

  return (
    <div className="flex flex-col gap-4">
      {milestones.map((m) => {
        const mSubs = m.id ? submissions[m.id] ?? [] : [];
        const mExts = m.id ? extensions[m.id] ?? [] : [];
        const mSetts = m.id ? settlements[m.id] ?? [] : [];
        const mDisps = m.id ? disputes[m.id] ?? [] : [];
        const isExpanded = expandedMilestone === m.id;

        const latestSub = mSubs[0];
        const pendingExt = mExts.find((e) => e.status === 'PENDING');
        const activeSett = mSetts.find((s) => s.status === 'ACTIVE' || s.status === 'AWAITING_PROPOSE_TX');
        const activeDisp = mDisps.find((d) => d.status === 'OPEN' || d.status === 'AWAITING_OPEN_TX' || d.status === 'DECIDED');

        const canFreelancerSubmit =
          isFreelancer &&
          agreement.status === 'FUNDED' &&
          (m.workStatus === 'IN_PROGRESS' || m.workStatus === 'REVISION_REQUESTED') &&
          m.chainStatus === 'OPEN';

        const canClientApprove =
          isClient &&
          latestSub?.reviewStatus === 'PENDING' &&
          m.workStatus === 'SUBMITTED' &&
          m.chainStatus === 'OPEN';

        return (
          <div key={m.id ?? m.position} className="rounded-lg border border-line bg-paper">
            {/* Header row */}
            <div className="flex flex-wrap items-center justify-between gap-4 p-4">
              <div className="flex items-center gap-3">
                <span className="flex size-7 items-center justify-center rounded-full bg-accent/10 font-display text-sm font-semibold text-accent">
                  {m.position + 1}
                </span>
                <div>
                  <h4 className="font-medium text-ink">{m.description}</h4>
                  <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-mute">
                    {m.amountInr != null && <span className="font-semibold text-ink">{formatInr(m.amountInr)}</span>}
                    {m.amountWei && <span>({weiToEth(m.amountWei)} ETH)</span>}
                    {m.effectiveDeadline && (
                      <span className="inline-flex items-center gap-1">
                        <Calendar className="size-3" /> Due {formatDate(m.effectiveDeadline)}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                {m.workStatus && <StatusBadge status={m.workStatus} />}
                {m.chainStatus && m.chainStatus !== 'OPEN' && (
                  <span className="rounded bg-line px-2 py-0.5 text-xs font-semibold">
                    {humanizeEnum(m.chainStatus)}
                  </span>
                )}
                <Button variant="ghost" size="sm" onClick={() => m.id && toggleExpand(m.id)}>
                  {isExpanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
                </Button>
              </div>
            </div>

            {/* Quick Actions Bar */}
            <div className="flex flex-wrap items-center gap-2 border-t border-line/60 bg-paper/50 px-4 py-2.5">
              {canFreelancerSubmit && (
                <Button
                  size="sm"
                  onClick={() => {
                    setSelectedMilestone(m);
                    setActiveModal('SUBMIT');
                  }}
                >
                  <Upload className="size-3.5" /> Submit Work
                </Button>
              )}

              {canClientApprove && latestSub && (
                <>
                  <Button
                    size="sm"
                    disabled={isPending}
                    onClick={() => handleApproveSubmission(m, latestSub.id)}
                  >
                    {isPending ? <Loader2 className="size-3.5 animate-spin" /> : <FileCheck className="size-3.5" />}
                    Approve & Release Payment
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      setSelectedMilestone(m);
                      setActiveModal('REVISION');
                    }}
                  >
                    Request Revision
                  </Button>
                </>
              )}

              {isFreelancer && (m.workStatus === 'IN_PROGRESS' || m.workStatus === 'REVISION_REQUESTED') && !pendingExt && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setSelectedMilestone(m);
                    setActiveModal('EXTENSION');
                  }}
                >
                  <Clock className="size-3.5" /> Request Extension
                </Button>
              )}

              {/* Settlement option */}
              {agreement.status === 'FUNDED' && m.chainStatus === 'OPEN' && !activeSett && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setSelectedMilestone(m);
                    setSettleFreelancerInr(String(Math.round((m.amountInr ?? 0) / 2)));
                    setActiveModal('SETTLEMENT');
                  }}
                >
                  <Scale className="size-3.5" /> Propose Settlement
                </Button>
              )}

              {/* Dispute option */}
              {agreement.status === 'FUNDED' && m.chainStatus === 'OPEN' && !activeDisp && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-brick hover:bg-brick/10"
                  onClick={() => {
                    setSelectedMilestone(m);
                    setActiveModal('DISPUTE');
                  }}
                >
                  <AlertTriangle className="size-3.5" /> Open Dispute
                </Button>
              )}
            </div>

            {/* Expanded Details Section */}
            {isExpanded && (
              <div className="flex flex-col gap-4 border-t border-line p-4 text-sm">
                {/* Active Dispute Banner */}
                {activeDisp && (
                  <div className="rounded-md border border-brick/40 bg-brick/10 p-4 text-brick">
                    <div className="flex items-center gap-2 font-medium">
                      <AlertTriangle className="size-4 shrink-0" />
                      Dispute #{activeDisp.id} ({activeDisp.status})
                    </div>
                    <p className="mt-1 text-xs text-ink">
                      Reason: <span className="font-semibold">{activeDisp.reason}</span>
                    </p>
                    {activeDisp.adminDecision && (
                      <p className="mt-2 text-xs font-semibold text-accent">
                        Admin Decision: {activeDisp.adminDecision} (Freelancer: {activeDisp.adminFreelancerWei ? `${weiToEth(activeDisp.adminFreelancerWei)} ETH` : '0'}, Client: {activeDisp.adminClientWei ? `${weiToEth(activeDisp.adminClientWei)} ETH` : '0'})
                      </p>
                    )}

                    {/* Responses List */}
                    <div className="mt-3 flex flex-col gap-2">
                      <span className="text-xs font-medium text-mute">Responses / Rebuttals:</span>
                      {activeDisp.responses?.map((r) => (
                        <div key={r.id} className="rounded bg-paper p-2 text-xs text-ink">
                          <span className="font-medium">{r.user?.displayName ?? 'Participant'}:</span> {r.response}
                          {r.evidenceLinks && (
                            <div className="mt-1 text-mute underline">
                              <a href={r.evidenceLinks} target="_blank" rel="noreferrer">
                                {r.evidenceLinks}
                              </a>
                            </div>
                          )}
                        </div>
                      ))}

                      {/* Reply Box */}
                      <div className="mt-2 flex flex-col gap-2">
                        <textarea
                          placeholder="Submit evidence or rebuttal..."
                          value={disputeReplyText}
                          onChange={(e) => setDisputeReplyText(e.target.value)}
                          className="w-full rounded border border-line bg-paper p-2 text-xs"
                          rows={2}
                        />
                        <input
                          placeholder="Evidence link (optional)"
                          value={disputeEvidence}
                          onChange={(e) => setDisputeEvidence(e.target.value)}
                          className="w-full rounded border border-line bg-paper p-1.5 text-xs"
                        />
                        <Button
                          size="sm"
                          onClick={() => m.id && handleReplyToDispute(activeDisp.id, m.id)}
                          className="self-end"
                        >
                          <Send className="size-3" /> Post Rebuttal
                        </Button>
                      </div>
                    </div>
                  </div>
                )}

                {/* Active Settlement Banner */}
                {activeSett && (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-accent/10 p-3 text-xs">
                    <div>
                      <p className="font-medium text-ink">
                        Settlement Proposed by {activeSett.proposer?.displayName ?? 'Party'}:
                      </p>
                      <p className="text-mute">
                        Freelancer gets {weiToEth(activeSett.freelancerWei)} ETH, Client refunded {weiToEth(activeSett.clientWei)} ETH.
                      </p>
                      {activeSett.reason && <p className="italic text-mute">"{activeSett.reason}"</p>}
                    </div>
                    {user?.id !== activeSett.proposer?.id && (
                      <Button size="sm" onClick={() => handleAcceptSettlement(m, activeSett)} disabled={isPending}>
                        Accept Settlement (MetaMask)
                      </Button>
                    )}
                  </div>
                )}

                {/* Pending Extension Request Banner */}
                {pendingExt && (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-amber-500/10 p-3 text-xs text-amber-900 dark:text-amber-200">
                    <div>
                      <p className="font-medium">Extension Requested: New Deadline {formatDate(pendingExt.requestedDeadline)}</p>
                      <p className="text-mute">Reason: {pendingExt.reason}</p>
                    </div>
                    {isClient && (
                      <div className="flex items-center gap-2">
                        <Button size="sm" onClick={() => m.id && handleDecideExtension(pendingExt.id, true, m.id)}>
                          Approve Extension
                        </Button>
                        <Button variant="outline" size="sm" onClick={() => m.id && handleDecideExtension(pendingExt.id, false, m.id)}>
                          Reject
                        </Button>
                      </div>
                    )}
                  </div>
                )}

                {/* Submissions History */}
                <div>
                  <h5 className="mb-2 font-medium text-ink">Submissions History ({mSubs.length})</h5>
                  {mSubs.length === 0 ? (
                    <p className="text-xs text-mute">No work submitted yet.</p>
                  ) : (
                    <div className="flex flex-col gap-2">
                      {mSubs.map((s) => (
                        <div key={s.id} className="rounded border border-line bg-paper/60 p-3 text-xs">
                          <div className="flex items-center justify-between">
                            <span className="font-medium">{formatDateTime(s.submittedAt)}</span>
                            <div className="flex items-center gap-1.5">
                              {s.late && <span className="text-brick font-semibold">[Late]</span>}
                              <StatusBadge status={s.reviewStatus} />
                            </div>
                          </div>
                          <p className="mt-1 whitespace-pre-wrap text-ink">{s.description}</p>
                          {s.links && (
                            <p className="mt-1 text-mute underline">
                              <a href={s.links} target="_blank" rel="noreferrer">
                                {s.links}
                              </a>
                            </p>
                          )}
                          {s.revisionReason && (
                            <div className="mt-2 rounded bg-brick/10 p-2 text-brick">
                              Revision note: {s.revisionReason}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        );
      })}

      {/* MODAL: Submit Work */}
      {activeModal === 'SUBMIT' && selectedMilestone && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-lg border border-line bg-paper p-6 shadow-xl">
            <h3 className="font-display text-lg font-semibold">Submit Milestone Work</h3>
            <p className="mt-1 text-sm text-mute">{selectedMilestone.description}</p>
            <div className="mt-4 flex flex-col gap-3">
              <div>
                <label className="text-xs font-medium text-mute">Work Description & Deliverables</label>
                <textarea
                  rows={4}
                  value={subDesc}
                  onChange={(e) => setSubDesc(e.target.value)}
                  placeholder="Detail the work accomplished, tests run, or deployment details..."
                  className="mt-1 w-full rounded-md border border-line bg-paper p-2.5 text-sm"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-mute">Deliverable Links (GitHub, Figma, Docs)</label>
                <input
                  type="text"
                  value={subLinks}
                  onChange={(e) => setSubLinks(e.target.value)}
                  placeholder="https://..."
                  className="mt-1 w-full rounded-md border border-line bg-paper p-2 text-sm"
                />
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" onClick={() => setActiveModal(null)}>
                Cancel
              </Button>
              <Button size="sm" onClick={handleSubmitWork} disabled={!subDesc.trim()}>
                Submit for Client Review
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Request Revision */}
      {activeModal === 'REVISION' && selectedMilestone && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-lg border border-line bg-paper p-6 shadow-xl">
            <h3 className="font-display text-lg font-semibold">Request Revisions</h3>
            <p className="mt-1 text-sm text-mute">Explain what needs to be changed before this milestone can be approved.</p>
            <div className="mt-4">
              <textarea
                rows={4}
                value={revReason}
                onChange={(e) => setRevReason(e.target.value)}
                placeholder="Explain the required changes or issues..."
                className="w-full rounded-md border border-line bg-paper p-2.5 text-sm"
              />
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" onClick={() => setActiveModal(null)}>
                Cancel
              </Button>
              <Button
                size="sm"
                onClick={() => {
                  const mSubs = selectedMilestone.id ? submissions[selectedMilestone.id] ?? [] : [];
                  if (mSubs[0]) handleRequestRevision(mSubs[0].id);
                }}
                disabled={!revReason.trim()}
              >
                Send Revision Request
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Request Extension */}
      {activeModal === 'EXTENSION' && selectedMilestone && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-lg border border-line bg-paper p-6 shadow-xl">
            <h3 className="font-display text-lg font-semibold">Request Deadline Extension</h3>
            <p className="mt-1 text-sm text-mute">
              Current Deadline: {formatDate(selectedMilestone.effectiveDeadline)}
            </p>
            <div className="mt-4 flex flex-col gap-3">
              <div>
                <label className="text-xs font-medium text-mute">Additional Days</label>
                <input
                  type="number"
                  min={1}
                  max={60}
                  value={extDays}
                  onChange={(e) => setExtDays(Number(e.target.value))}
                  className="mt-1 w-full rounded-md border border-line bg-paper p-2 text-sm"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-mute">Reason for Extension</label>
                <textarea
                  rows={3}
                  value={extReason}
                  onChange={(e) => setExtReason(e.target.value)}
                  placeholder="Explain why extra time is needed..."
                  className="mt-1 w-full rounded-md border border-line bg-paper p-2 text-sm"
                />
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" onClick={() => setActiveModal(null)}>
                Cancel
              </Button>
              <Button size="sm" onClick={handleRequestExtension} disabled={!extReason.trim()}>
                Submit Request
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Propose Settlement */}
      {activeModal === 'SETTLEMENT' && selectedMilestone && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-lg border border-line bg-paper p-6 shadow-xl">
            <h3 className="font-display text-lg font-semibold">Propose Settlement</h3>
            <p className="mt-1 text-sm text-mute">
              Total Milestone Allocation: {formatInr(selectedMilestone.amountInr ?? 0)}
            </p>
            <div className="mt-4 flex flex-col gap-3">
              <div>
                <label className="text-xs font-medium text-mute">Freelancer Share (INR)</label>
                <input
                  type="number"
                  value={settleFreelancerInr}
                  onChange={(e) => setSettleFreelancerInr(e.target.value)}
                  className="mt-1 w-full rounded-md border border-line bg-paper p-2 text-sm"
                />
                <p className="mt-1 text-xs text-mute">
                  Client will be refunded:{' '}
                  {formatInr(Math.max(0, (selectedMilestone.amountInr ?? 0) - (Number(settleFreelancerInr) || 0)))}
                </p>
              </div>
              <div>
                <label className="text-xs font-medium text-mute">Settlement Note</label>
                <textarea
                  rows={3}
                  value={settleReason}
                  onChange={(e) => setSettleReason(e.target.value)}
                  placeholder="Terms of mutual settlement..."
                  className="mt-1 w-full rounded-md border border-line bg-paper p-2 text-sm"
                />
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" onClick={() => setActiveModal(null)}>
                Cancel
              </Button>
              <Button size="sm" onClick={handleProposeSettlement} disabled={isPending || !settleFreelancerInr}>
                {isPending ? 'Signing Tx...' : 'Sign & Propose Settlement'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Open Dispute */}
      {activeModal === 'DISPUTE' && selectedMilestone && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-lg border border-line bg-paper p-6 shadow-xl">
            <h3 className="flex items-center gap-2 font-display text-lg font-semibold text-brick">
              <AlertTriangle className="size-5" /> Open Dispute
            </h3>
            <p className="mt-1 text-sm text-mute">
              Opening a dispute freezes this milestone on-chain. It cannot be approved or settled mutually; platform admin will review evidence and make a final allocation decision.
            </p>
            <div className="mt-4">
              <label className="text-xs font-medium text-mute">Dispute Reason & Claim</label>
              <textarea
                rows={4}
                value={disputeReason}
                onChange={(e) => setDisputeReason(e.target.value)}
                placeholder="Explain the breach of terms, lack of delivery, or dispute details..."
                className="mt-1 w-full rounded-md border border-line bg-paper p-2.5 text-sm"
              />
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" onClick={() => setActiveModal(null)}>
                Cancel
              </Button>
              <Button
                size="sm"
                className="bg-brick hover:bg-brick/90 text-white"
                onClick={handleOpenDispute}
                disabled={isPending || !disputeReason.trim()}
              >
                {isPending ? 'Opening Dispute in MetaMask...' : 'Open Dispute (MetaMask)'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
