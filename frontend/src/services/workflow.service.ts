import { guard } from '../utils/errors';
import { api } from './api';

export interface ChainConfig {
  chainId: number;
  factoryAddress: string;
  adminAddress: string;
  requiredConfirmations: number;
  inrPerEth: number;
}

export interface SubmissionItem {
  id: number;
  milestoneId: number;
  description: string;
  links?: string | null;
  submittedAt: string;
  late: boolean;
  reviewStatus: 'PENDING' | 'APPROVED' | 'REVISION_REQUESTED' | 'SUPERSEDED';
  revisionReason?: string | null;
  reviewedAt?: string | null;
}

export interface ExtensionItem {
  id: number;
  milestoneId: number;
  requestedBy?: { id: number; displayName: string };
  currentDeadline: string;
  requestedDeadline: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  decidedBy?: { id: number; displayName: string };
  decisionNote?: string | null;
  requestedAt: string;
  decidedAt?: string | null;
}

export interface SettlementItem {
  id: number;
  milestoneId: number;
  proposer?: { id: number; displayName: string };
  freelancerWei: string;
  clientWei: string;
  reason?: string | null;
  status: 'AWAITING_PROPOSE_TX' | 'ACTIVE' | 'EXECUTED' | 'WITHDRAWN' | 'SUPERSEDED' | 'CANCELLED';
  proposeTxHash?: string | null;
  executedTxHash?: string | null;
  createdAt: string;
  decidedAt?: string | null;
}

export interface DisputeReplyItem {
  id: number;
  disputeId: number;
  user?: { id: number; displayName: string };
  response: string;
  evidenceLinks?: string | null;
  createdAt: string;
}

export interface DisputeDetail {
  id: number;
  milestoneId: number;
  openedBy?: { id: number; displayName: string };
  reason: string;
  reasonHash?: string | null;
  status: 'AWAITING_OPEN_TX' | 'OPEN' | 'DECIDED' | 'RESOLVED' | 'CANCELLED';
  adminDecision?: 'FREELANCER_FULL' | 'CLIENT_FULL' | 'SPLIT' | null;
  adminFreelancerWei?: string | null;
  adminClientWei?: string | null;
  adminExplanation?: string | null;
  resolutionHash?: string | null;
  decidedBy?: { id: number; displayName: string };
  decidedAt?: string | null;
  openTxHash?: string | null;
  resolvedTxHash?: string | null;
  createdAt: string;
  updatedAt: string;
  responses: DisputeReplyItem[];
}

export interface DisputeSummary {
  id: number;
  milestoneId: number;
  agreementId: number;
  openedBy?: { id: number; displayName: string };
  reason: string;
  status: string;
  adminDecision?: string | null;
  milestoneAmountWei: string;
  createdAt: string;
}

export interface ReviewItem {
  id: number;
  agreementId: number;
  reviewer?: { id: number; displayName: string };
  reviewee?: { id: number; displayName: string };
  rating: number;
  comment?: string | null;
  createdAt: string;
}

export interface UserReviews {
  userId: number;
  totalReviews: number;
  averageRating: number | null;
  reviews: ReviewItem[];
}

export const workflowApi = {
  async getChainConfig(): Promise<ChainConfig> {
    const { data } = await guard(api.get<ChainConfig>('/api/chain/config'));
    return data;
  },

  async linkWallet(agreementId: number, address: string) {
    const { data } = await guard(api.post(`/api/agreements/${agreementId}/wallets`, { address }));
    return data;
  },

  async generateMilestones(agreementId: number) {
    const { data } = await guard(api.post(`/api/agreements/${agreementId}/milestones/generate`));
    return data;
  },

  async registerEscrow(agreementId: number, txHash: string) {
    const { data } = await guard(api.post(`/api/agreements/${agreementId}/escrow/register`, { txHash }));
    return data;
  },

  async verifyFunding(agreementId: number, txHash: string) {
    const { data } = await guard(api.post(`/api/agreements/${agreementId}/escrow/verify-fund`, { txHash }));
    return data;
  },

  async reconcile(agreementId: number) {
    const { data } = await guard(api.post(`/api/agreements/${agreementId}/reconcile`));
    return data;
  },

  // Submissions
  async submitWork(milestoneId: number, description: string, links?: string): Promise<SubmissionItem> {
    const { data } = await guard(api.post<SubmissionItem>(`/api/milestones/${milestoneId}/submissions`, { description, links }));
    return data;
  },

  async getSubmissions(milestoneId: number): Promise<SubmissionItem[]> {
    const { data } = await guard(api.get<SubmissionItem[]>(`/api/milestones/${milestoneId}/submissions`));
    return data;
  },

  async approveSubmission(submissionId: number, txHash?: string): Promise<SubmissionItem> {
    const { data } = await guard(api.post<SubmissionItem>(`/api/submissions/${submissionId}/approve`, { txHash }));
    return data;
  },

  async requestRevision(submissionId: number, reason: string): Promise<SubmissionItem> {
    const { data } = await guard(api.post<SubmissionItem>(`/api/submissions/${submissionId}/request-revision`, { reason }));
    return data;
  },

  // Extensions
  async requestExtension(milestoneId: number, requestedDeadline: string, reason: string): Promise<ExtensionItem> {
    const { data } = await guard(
      api.post<ExtensionItem>(`/api/milestones/${milestoneId}/extensions`, { requestedDeadline, reason })
    );
    return data;
  },

  async getExtensions(milestoneId: number): Promise<ExtensionItem[]> {
    const { data } = await guard(api.get<ExtensionItem[]>(`/api/milestones/${milestoneId}/extensions`));
    return data;
  },

  async approveExtension(extensionId: number, note?: string): Promise<ExtensionItem> {
    const { data } = await guard(api.post<ExtensionItem>(`/api/extensions/${extensionId}/approve`, { note }));
    return data;
  },

  async rejectExtension(extensionId: number, note?: string): Promise<ExtensionItem> {
    const { data } = await guard(api.post<ExtensionItem>(`/api/extensions/${extensionId}/reject`, { note }));
    return data;
  },

  // Settlements
  async proposeSettlement(
    milestoneId: number,
    freelancerWei: string,
    clientWei: string,
    reason?: string,
    txHash?: string
  ): Promise<SettlementItem> {
    const { data } = await guard(
      api.post<SettlementItem>(`/api/milestones/${milestoneId}/settlements`, {
        freelancerWei,
        clientWei,
        reason,
        txHash,
      })
    );
    return data;
  },

  async getSettlements(milestoneId: number): Promise<SettlementItem[]> {
    const { data } = await guard(api.get<SettlementItem[]>(`/api/milestones/${milestoneId}/settlements`));
    return data;
  },

  async acceptSettlement(settlementId: number, txHash: string): Promise<SettlementItem> {
    const { data } = await guard(api.post<SettlementItem>(`/api/settlements/${settlementId}/accept`, { txHash }));
    return data;
  },

  async withdrawSettlement(settlementId: number): Promise<SettlementItem> {
    const { data } = await guard(api.post<SettlementItem>(`/api/settlements/${settlementId}/withdraw`));
    return data;
  },

  // Disputes
  async openDispute(milestoneId: number, reason: string, txHash?: string): Promise<DisputeDetail> {
    const { data } = await guard(api.post<DisputeDetail>(`/api/milestones/${milestoneId}/disputes`, { reason, txHash }));
    return data;
  },

  async getDisputesForMilestone(milestoneId: number): Promise<DisputeDetail[]> {
    const { data } = await guard(api.get<DisputeDetail[]>(`/api/milestones/${milestoneId}/disputes`));
    return data;
  },

  async getDispute(disputeId: number): Promise<DisputeDetail> {
    const { data } = await guard(api.get<DisputeDetail>(`/api/disputes/${disputeId}`));
    return data;
  },

  async replyToDispute(disputeId: number, response: string, evidenceLinks?: string): Promise<DisputeReplyItem> {
    const { data } = await guard(
      api.post<DisputeReplyItem>(`/api/disputes/${disputeId}/responses`, { response, evidenceLinks })
    );
    return data;
  },

  async adminListDisputes(status?: string, page = 0, size = 20) {
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    params.set('page', String(page));
    params.set('size', String(size));
    const { data } = await guard(api.get<{ content: DisputeSummary[]; totalElements: number; totalPages: number }>(`/api/admin/disputes?${params.toString()}`));
    return data;
  },

  async adminRecordDecision(
    disputeId: number,
    decision: 'FREELANCER_FULL' | 'CLIENT_FULL' | 'SPLIT',
    explanation: string,
    freelancerWei?: string,
    clientWei?: string
  ): Promise<DisputeDetail> {
    const { data } = await guard(
      api.post<DisputeDetail>(`/api/admin/disputes/${disputeId}/decision`, {
        decision,
        explanation,
        freelancerWei,
        clientWei,
      })
    );
    return data;
  },

  async adminSubmitResolutionTx(disputeId: number, txHash: string): Promise<DisputeDetail> {
    const { data } = await guard(api.post<DisputeDetail>(`/api/admin/disputes/${disputeId}/resolution-tx`, { txHash }));
    return data;
  },

  // Reviews
  async createReview(agreementId: number, rating: number, comment?: string): Promise<ReviewItem> {
    const { data } = await guard(api.post<ReviewItem>(`/api/agreements/${agreementId}/reviews`, { rating, comment }));
    return data;
  },

  async getUserReviews(userId: number): Promise<UserReviews> {
    const { data } = await guard(api.get<UserReviews>(`/api/users/${userId}/reviews`));
    return data;
  },
};
