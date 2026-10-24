import type {
  Agreement,
  AgreementMilestone,
  NegotiationMessage,
  NegotiationThread,
  ProposedTerms,
  Project,
  ProjectInput,
  ProjectQuery,
  Proposal,
  ProposalInput,
} from '../types/marketplace';

/**
 * The service boundary for everything the backend has NOT built yet.
 * Each interface has two implementations: the live one (services/*.service.ts, calling the endpoints documented in
 * ARCHITECTURE.md section 4) and a labelled sample-data one (src/mocks). VITE_USE_MOCKS picks between them.
 * Pages depend only on these interfaces.
 */
export interface ProjectsApi {
  /** GET /api/projects (filters) */
  list(query: ProjectQuery): Promise<Project[]>;
  /** GET /api/projects/{id} */
  get(id: number): Promise<Project>;
  /** Projects owned by the signed-in client. No dedicated endpoint is documented; see projects.service.ts. */
  mine(): Promise<Project[]>;
  /** POST /api/projects, then POST /api/projects/{id}/publish when `publish` is true */
  create(input: ProjectInput, publish: boolean): Promise<Project>;
  /** PUT /api/projects/{id} */
  update(id: number, input: ProjectInput): Promise<Project>;
  /** POST /api/projects/{id}/publish */
  publish(id: number): Promise<Project>;
  /** POST /api/projects/{id}/close */
  close(id: number): Promise<Project>;
}

export interface ProposalsApi {
  /** POST /api/projects/{id}/proposals */
  submit(projectId: number, input: ProposalInput): Promise<Proposal>;
  /** GET /api/projects/{id}/proposals (project owner only) */
  forProject(projectId: number): Promise<Proposal[]>;
  /** GET /api/proposals/mine */
  mine(): Promise<Proposal[]>;
  /** POST /api/proposals/{id}/shortlist */
  shortlist(id: number): Promise<Proposal>;
  /** POST /api/proposals/{id}/accept */
  accept(id: number): Promise<Proposal>;
  /** POST /api/proposals/{id}/reject */
  reject(id: number): Promise<Proposal>;
}

export interface NegotiationApi {
  /** GET /api/proposals/{id}/negotiation */
  thread(proposalId: number): Promise<NegotiationThread>;
  /** POST /api/proposals/{id}/negotiation */
  send(proposalId: number, body: { message: string; terms?: ProposedTerms }): Promise<NegotiationMessage>;
}

export interface AgreementsApi {
  /** GET /api/agreements/mine */
  mine(): Promise<Agreement[]>;
  /** GET /api/agreements/{id} */
  get(id: number): Promise<Agreement>;
  /** GET /api/agreements/{id}/milestones */
  milestones(id: number): Promise<AgreementMilestone[]>;
  /** POST /api/agreements/{id}/counter */
  counter(id: number, body: { message: string; terms?: ProposedTerms }): Promise<Agreement>;
  /** POST /api/agreements/{id}/finalize. The agreement is only final when the RESPONSE says so. */
  finalize(id: number): Promise<Agreement>;
}
