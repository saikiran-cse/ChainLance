/**
 * Registry of every backend endpoint the frontend knows about, and whether it exists today.
 * "live"    = a controller method exists in backend/src/main/java/com/chainlance/controller (verified by reading it).
 * "pending" = documented in ARCHITECTURE.md section 4 but no controller exists yet.
 * Audited against: saikiran-cse/chainlance main, commit fc94164 "Baseline: Stages 1-3". Update this file when the
 * backend changes; the in-app "API status" page renders it.
 */
export type EndpointStatus = 'live' | 'pending';
export type HttpMethod = 'GET' | 'POST' | 'PUT';

export interface EndpointSpec {
  method: HttpMethod;
  path: string;
  area: string;
  status: EndpointStatus;
  /** Which frontend feature depends on it */
  usedBy: string;
  note?: string;
}

const live = (method: HttpMethod, path: string, area: string, usedBy: string, note?: string): EndpointSpec => ({
  method,
  path,
  area,
  status: 'live',
  usedBy,
  note,
});
const pending = (method: HttpMethod, path: string, area: string, usedBy: string, note?: string): EndpointSpec => ({
  method,
  path,
  area,
  status: 'pending',
  usedBy,
  note,
});

export const ENDPOINTS: EndpointSpec[] = [
  // ---- Auth and profile (live)
  live('POST', '/api/auth/register', 'Auth', 'Register page', 'Returns a token, so registering also signs the user in.'),
  live('POST', '/api/auth/login', 'Auth', 'Login page'),
  live('GET', '/api/users/me', 'Auth', 'Session restore, profile'),
  live('PUT', '/api/users/me', 'Auth', 'Profile page', 'Only displayName and bio are editable.'),

  // ---- Wallet (live)
  live('POST', '/api/wallet/challenge', 'Wallet', 'Wallet page', 'Returns the exact text to sign.'),
  live('POST', '/api/wallet/verify', 'Wallet', 'Wallet page', 'The only source of truth for "verified".'),
  live('GET', '/api/wallet', 'Wallet', 'Wallet page, dashboards'),

  // ---- Projects (live in Stage 4)
  live('GET', '/api/projects', 'Projects', 'Browse projects, freelancer dashboard', 'Returns PageResponse of open projects, authenticated.'),
  live('POST', '/api/projects', 'Projects', 'Create project (starts in DRAFT)'),
  live('GET', '/api/projects/mine', 'Projects', 'Client dashboard, my projects'),
  live('GET', '/api/projects/{id}', 'Projects', 'Project details, edit project'),
  live('PUT', '/api/projects/{id}', 'Projects', 'Edit project (while DRAFT/OPEN)'),
  live('POST', '/api/projects/{id}/publish', 'Projects', 'Publish draft to open'),
  live('POST', '/api/projects/{id}/close', 'Projects', 'Close open project'),
  live('POST', '/api/projects/{id}/cancel', 'Projects', 'Cancel draft or open project'),

  // ---- Proposals (live in Stage 4)
  live('POST', '/api/projects/{projectId}/proposals', 'Proposals', 'Submit proposal (freelancer, creates offer #1)'),
  live('GET', '/api/projects/{projectId}/proposals', 'Proposals', 'Proposal review (owner only)'),
  live('GET', '/api/proposals/my', 'Proposals', 'My proposals, freelancer dashboard'),
  live('GET', '/api/proposals/{id}', 'Proposals', 'Proposal details with offers history'),
  live('POST', '/api/proposals/{id}/shortlist', 'Proposals', 'Shortlist proposal (owner only)'),
  live('POST', '/api/proposals/{id}/reject', 'Proposals', 'Reject proposal (owner only)'),
  live('POST', '/api/proposals/{id}/withdraw', 'Proposals', 'Withdraw proposal (freelancer only)'),

  // ---- Offers & Negotiation (live in Stage 4)
  live('GET', '/api/proposals/{proposalId}/offers', 'Offers', 'Negotiation offers history'),
  live('POST', '/api/proposals/{proposalId}/offers', 'Offers', 'Make new offer when none is pending'),
  live('POST', '/api/proposals/{proposalId}/offers/{offerId}/accept', 'Offers', 'Accept pending offer (other party)'),
  live('POST', '/api/proposals/{proposalId}/offers/{offerId}/counter', 'Offers', 'Counter pending offer (other party)'),
  live('POST', '/api/proposals/{proposalId}/offers/{offerId}/withdraw', 'Offers', 'Withdraw pending offer (author only)'),

  // ---- Agreements (live in Stage 4)
  live('POST', '/api/proposals/{id}/accept', 'Agreements', 'Accept proposal and freeze agreement snapshot'),
  live('GET', '/api/agreements/my', 'Agreements', 'Agreements list, dashboards'),
  live('GET', '/api/agreements/{id}', 'Agreements', 'Agreement details and snapshot integrity'),

  // ---- Future increments (pending)
  pending('GET', '/api/chain/config', 'Chain', 'Phase 2: Chain config'),
  pending('POST', '/api/agreements/{id}/wallets', 'Agreements', 'Phase 2: Wallet selection'),
  pending('POST', '/api/agreements/{id}/milestones/generate', 'Milestones', 'Phase 2: Generate milestones'),
  pending('PUT', '/api/projects/{id}/milestone-plan', 'Projects', 'Phase 2: Milestone plan editor'),
  pending('GET', '/api/agreements/{id}/milestones', 'Agreements', 'Phase 2: Milestone list'),
  pending('POST', '/api/agreements/{id}/escrow/register', 'Escrow', 'Phase 3: Escrow registration'),
  pending('POST', '/api/transactions/verify', 'Escrow', 'Phase 3: Verify on-chain transaction'),
  pending('GET', '/api/transactions/mine', 'Escrow', 'Phase 3: Transaction history'),
  pending('POST', '/api/agreements/{id}/reconcile', 'Escrow', 'Phase 3: Reconcile contract with DB'),
  pending('POST', '/api/milestones/{id}/submissions', 'Submissions', 'Phase 4: Submit work'),
  pending('POST', '/api/submissions/{id}/approve', 'Submissions', 'Phase 4: Approve work and release'),
  pending('POST', '/api/submissions/{id}/request-revision', 'Submissions', 'Phase 4: Request revision'),
  pending('POST', '/api/milestones/{id}/extensions', 'Deadlines', 'Phase 4: Request extension'),
  pending('POST', '/api/extensions/{id}/approve', 'Deadlines', 'Phase 4: Approve extension'),
  pending('POST', '/api/extensions/{id}/reject', 'Deadlines', 'Phase 4: Reject extension'),
  pending('POST', '/api/milestones/{id}/settlements', 'Settlement', 'Phase 4: Propose settlement'),
  pending('POST', '/api/settlements/{id}/accept', 'Settlement', 'Phase 4: Accept settlement'),
  pending('POST', '/api/settlements/{id}/withdraw', 'Settlement', 'Phase 4: Withdraw settlement'),
  pending('POST', '/api/milestones/{id}/disputes', 'Disputes', 'Phase 4: Open dispute'),
  pending('POST', '/api/disputes/{id}/responses', 'Disputes', 'Phase 4: Respond to dispute'),
  pending('GET', '/api/disputes/mine', 'Disputes', 'Phase 4: My disputes'),
  pending('GET', '/api/admin/disputes', 'Admin', 'Phase 4: Admin dispute queue'),
  pending('GET', '/api/admin/disputes/{id}', 'Admin', 'Phase 4: Admin dispute detail'),
  pending('POST', '/api/admin/disputes/{id}/decision', 'Admin', 'Phase 4: Admin dispute decision'),
  pending('POST', '/api/admin/disputes/{id}/resolution-tx', 'Admin', 'Phase 4: Admin resolution tx'),
  pending('POST', '/api/agreements/{id}/reviews', 'Reviews', 'Phase 4: Submit review'),
  pending('GET', '/api/users/{id}/reviews', 'Reviews', 'Phase 4: User reviews'),
];

export function endpointCounts(): { live: number; pending: number } {
  let liveCount = 0;
  for (const e of ENDPOINTS) if (e.status === 'live') liveCount += 1;
  return { live: liveCount, pending: ENDPOINTS.length - liveCount };
}
