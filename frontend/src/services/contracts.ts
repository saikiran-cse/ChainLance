import type {
  Agreement,
  AgreementMilestone,
  NegotiationMessage,
  NegotiationThread,
  Offer,
  PageResponse,
  ProposedTerms,
  Project,
  ProjectInput,
  ProjectQuery,
  Proposal,
  ProposalInput,
} from '../types/marketplace';

/**
 * The service boundary for marketplace operations.
 * Each interface has a live implementation (services/*.service.ts) and a sample-data one (src/mocks).
 */
export interface ProjectsApi {
  /** GET /api/projects (filters and paging) */
  list(query?: ProjectQuery, page?: number, size?: number): Promise<PageResponse<Project>>;
  /** GET /api/projects/{id} */
  get(id: number): Promise<Project>;
  /** GET /api/projects/mine */
  mine(): Promise<Project[]>;
  /** POST /api/projects, then POST /api/projects/{id}/publish when `publish` is true */
  create(input: ProjectInput, publish: boolean): Promise<Project>;
  /** PUT /api/projects/{id} */
  update(id: number, input: ProjectInput): Promise<Project>;
  /** POST /api/projects/{id}/publish */
  publish(id: number): Promise<Project>;
  /** POST /api/projects/{id}/close */
  close(id: number): Promise<Project>;
  /** POST /api/projects/{id}/cancel */
  cancel(id: number): Promise<Project>;
}

export interface ProposalsApi {
  /** POST /api/projects/{id}/proposals */
  submit(projectId: number, input: ProposalInput): Promise<Proposal>;
  /** GET /api/projects/{id}/proposals (project owner only) */
  forProject(projectId: number): Promise<Proposal[]>;
  /** GET /api/proposals/my */
  mine(): Promise<Proposal[]>;
  /** GET /api/proposals/{id} */
  get(id: number): Promise<Proposal>;
  /** POST /api/proposals/{id}/shortlist */
  shortlist(id: number): Promise<Proposal>;
  /** POST /api/proposals/{id}/accept (creates agreement snapshot in one tx) */
  accept(id: number): Promise<Agreement>;
  /** POST /api/proposals/{id}/reject */
  reject(id: number): Promise<Proposal>;
  /** POST /api/proposals/{id}/withdraw */
  withdraw(id: number): Promise<Proposal>;
}

export interface NegotiationApi {
  /** GET /api/proposals/{id}/offers */
  offers(proposalId: number): Promise<Offer[]>;
  /** POST /api/proposals/{id}/offers */
  makeOffer(proposalId: number, input: { priceInr: number; durationDays: number; note?: string }): Promise<Offer>;
  /** POST /api/proposals/{id}/offers/{offerId}/accept */
  acceptOffer(proposalId: number, offerId: number): Promise<Offer>;
  /** POST /api/proposals/{id}/offers/{offerId}/counter */
  counterOffer(proposalId: number, offerId: number, input: { priceInr: number; durationDays: number; note?: string }): Promise<Offer>;
  /** POST /api/proposals/{id}/offers/{offerId}/withdraw */
  withdrawOffer(proposalId: number, offerId: number): Promise<Offer>;
  /** Load proposal with offers and any agreement */
  thread(proposalId: number): Promise<NegotiationThread>;
  /** Send offer or message */
  send(proposalId: number, body: { message: string; terms?: ProposedTerms }): Promise<NegotiationMessage | Offer>;
}

export interface AgreementsApi {
  /** GET /api/agreements/my */
  mine(): Promise<Agreement[]>;
  /** GET /api/agreements/{id} */
  get(id: number): Promise<Agreement>;
  /** GET /api/agreements/{id}/milestones (Phase 2) */
  milestones(id: number): Promise<AgreementMilestone[]>;
}
