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

export const live = (method: HttpMethod, path: string, area: string, usedBy: string, note?: string): EndpointSpec => ({
  method,
  path,
  area,
  status: 'live',
  usedBy,
  note,
});
export const pending = (method: HttpMethod, path: string, area: string, usedBy: string, note?: string): EndpointSpec => ({
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

  // ---- Chain & Escrow (live)
  live('GET', '/api/chain/config', 'Chain', 'Chain configuration, addresses & demo rate'),
  live('POST', '/api/agreements/{id}/wallets', 'Agreements', 'Link verified wallet to agreement'),
  live('PUT', '/api/projects/{id}/milestone-plan', 'Projects', 'Define proportional milestone plan'),
  live('GET', '/api/projects/{id}/milestone-plan', 'Projects', 'Retrieve milestone plan items'),
  live('POST', '/api/agreements/{id}/milestones/generate', 'Milestones', 'Generate scaled milestones (zero remainder loss)'),
  live('GET', '/api/agreements/{id}/milestones', 'Milestones', 'List agreement milestones'),
  live('GET', '/api/milestones/{id}', 'Milestones', 'Milestone detail'),
  live('POST', '/api/agreements/{id}/escrow/register', 'Escrow', 'Register on-chain escrow deployment tx'),
  live('POST', '/api/agreements/{id}/escrow/verify-fund', 'Escrow', 'Verify funding deposit on-chain; activate Milestone 0'),
  live('POST', '/api/agreements/{id}/reconcile', 'Escrow', 'Reconcile on-chain contract state with MySQL'),
  live('GET', '/api/transactions/mine', 'Escrow', 'User on-chain transaction log history'),

  // ---- Submissions & Revisions (live)
  live('POST', '/api/milestones/{id}/submissions', 'Submissions', 'Freelancer submits work deliverables'),
  live('GET', '/api/milestones/{id}/submissions', 'Submissions', 'List milestone submissions'),
  live('POST', '/api/submissions/{id}/approve', 'Submissions', 'Client approves submission & releases payout'),
  live('POST', '/api/submissions/{id}/request-revision', 'Submissions', 'Client requests revisions with feedback'),

  // ---- Deadline Extensions (live)
  live('POST', '/api/milestones/{id}/extensions', 'Deadlines', 'Freelancer requests deadline extension'),
  live('GET', '/api/milestones/{id}/extensions', 'Deadlines', 'List milestone extension requests'),
  live('POST', '/api/extensions/{id}/approve', 'Deadlines', 'Client approves deadline extension'),
  live('POST', '/api/extensions/{id}/reject', 'Deadlines', 'Client rejects deadline extension'),

  // ---- Mutual Settlements (live)
  live('POST', '/api/milestones/{id}/settlements', 'Settlement', 'Propose agreed mutual split'),
  live('GET', '/api/milestones/{id}/settlements', 'Settlement', 'List milestone settlement proposals'),
  live('POST', '/api/settlements/{id}/accept', 'Settlement', 'Accept settlement & verify on-chain payout split'),
  live('POST', '/api/settlements/{id}/withdraw', 'Settlement', 'Proposer withdraws active settlement'),

  // ---- Disputes & Arbitration (live)
  live('POST', '/api/milestones/{id}/disputes', 'Disputes', 'Open dispute on-chain & freeze milestone'),
  live('GET', '/api/milestones/{id}/disputes', 'Disputes', 'List disputes for milestone'),
  live('GET', '/api/disputes/{id}', 'Disputes', 'Dispute detail and rebuttal thread'),
  live('POST', '/api/disputes/{id}/responses', 'Disputes', 'Submit evidence or rebuttal into dispute'),
  live('GET', '/api/admin/disputes', 'Admin', 'Admin paginated dispute arbitration queue'),
  live('POST', '/api/admin/disputes/{id}/decision', 'Admin', 'Admin records binding allocation decision'),
  live('POST', '/api/admin/disputes/{id}/resolution-tx', 'Admin', 'Admin executes on-chain resolution tx & distributes funds'),

  // ---- Reviews & Feedback (live)
  live('POST', '/api/agreements/{id}/reviews', 'Reviews', 'Submit two-way star rating & review upon completion'),
  live('GET', '/api/users/{id}/reviews', 'Reviews', 'Public user ratings and reviews'),
];

export function endpointCounts(): { live: number; pending: number } {
  let liveCount = 0;
  for (const e of ENDPOINTS) if (e.status === 'live') liveCount += 1;
  return { live: liveCount, pending: ENDPOINTS.length - liveCount };
}
