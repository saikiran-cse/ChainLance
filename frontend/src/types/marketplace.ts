/**
 * ASSUMED CONTRACT. The marketplace controllers, services and DTOs do NOT exist in the backend yet.
 * These shapes are derived from the JPA entities and db/migration/V1__init.sql (field names and status enums are
 * exact; JSON envelopes are a best guess). All backend responses pass through services/adapters.ts, so when the real
 * DTOs land, only the adapters and this file need to change, not the pages.
 */

export type ProjectStatus = 'DRAFT' | 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED' | 'CANCELLED';
export type ProposalStatus = 'SUBMITTED' | 'SHORTLISTED' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
export type OfferStatus = 'PENDING' | 'ACCEPTED' | 'COUNTERED' | 'WITHDRAWN' | 'CLOSED';
export type AgreementStatus = 'DRAFT' | 'FINALIZED' | 'FUNDED' | 'COMPLETED' | 'CANCELLED';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** project_milestone_plans row */
export interface MilestonePlanItem {
  position: number;
  description: string;
  allocationInr: number;
}

export interface Project {
  id: number;
  clientId: number;
  clientName?: string;
  title: string;
  description: string;
  category: string;
  /** DB column is one VARCHAR(500); the UI always works with a list. */
  skills: string[];
  budgetInr: number;
  deadline?: string | null;
  status: ProjectStatus;
  milestones: MilestonePlanItem[];
  createdAt: string;
  updatedAt?: string;
}

export interface ProjectInput {
  title: string;
  description: string;
  category: string;
  skills: string[];
  budgetInr: number;
  deadline?: string | null;
  milestones: MilestonePlanItem[];
}

export interface ProjectQuery {
  q?: string;
  category?: string;
  skill?: string;
}

export interface Offer {
  id: number;
  proposalId: number;
  offeredBy: number;
  priceInr: number;
  durationDays: number;
  note?: string;
  status: OfferStatus;
  respondedBy?: number | null;
  respondedAt?: string | null;
  createdAt: string;
}

export interface Proposal {
  id: number;
  projectId: number;
  projectTitle?: string;
  freelancerId: number;
  freelancerName?: string;
  message: string;
  priceInr: number;
  durationDays: number;
  portfolioLinks: string[];
  status: ProposalStatus;
  createdAt: string;
  updatedAt?: string;
  offers?: Offer[];
}

export interface ProposalInput {
  message: string;
  priceInr: number;
  durationDays: number;
  portfolioLinks: string[];
}

/** negotiation_messages.proposed_terms_json is free-form JSON; these are the keys the UI reads and writes. */
export interface ProposedTerms {
  priceInr?: number;
  durationDays?: number;
}

export interface NegotiationMessage {
  id: number;
  proposalId: number;
  senderId: number;
  senderName?: string;
  message: string;
  proposedTerms?: ProposedTerms | null;
  createdAt: string;
}

export interface NegotiationThread {
  proposal: Proposal;
  messages: NegotiationMessage[];
  /** Present once the backend has created an agreement for this proposal. */
  agreement?: Agreement | null;
}

export interface AgreementMilestone {
  id?: number;
  position: number;
  description: string;
  acceptanceCriteria?: string | null;
  /** wei as a decimal string (DECIMAL(38,0)); never a JS number */
  amountWei?: string | null;
  amountInr?: number | null;
  effectiveDeadline?: string | null;
  workStatus?: string;
  chainStatus?: string;
}

export interface Agreement {
  id: number;
  proposalId: number;
  projectId?: number;
  projectTitle?: string;
  projectDescription?: string;
  clientId?: number;
  clientName?: string;
  freelancerId?: number;
  freelancerName?: string;
  /** wei as a decimal string; may be absent before the escrow amount is computed */
  totalWei?: string | null;
  /** agreed price in INR when the backend includes it (usually inside the snapshot) */
  priceInr?: number | null;
  deadline?: string | null;
  status: AgreementStatus;
  clientFinalizedAt?: string | null;
  freelancerFinalizedAt?: string | null;
  snapshot?: Record<string, unknown> | null;
  snapshotHash?: string | null;
  snapshotIntegrityOk?: boolean;
  clientWallet?: string | null;
  freelancerWallet?: string | null;
  escrowAddress?: string | null;
  chainId?: number | null;
  milestones: AgreementMilestone[];
  createdAt?: string;
}
