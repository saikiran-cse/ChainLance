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

  // ---- Projects (pending)
  pending(
    'GET',
    '/api/projects',
    'Projects',
    'Browse projects, freelancer dashboard',
    'SecurityConfig currently has no permitAll rule for this path, so anonymous browsing needs a backend rule.',
  ),
  pending('POST', '/api/projects', 'Projects', 'Create project'),
  pending('GET', '/api/projects/{id}', 'Projects', 'Project details, edit project'),
  pending('PUT', '/api/projects/{id}', 'Projects', 'Edit project'),
  pending('POST', '/api/projects/{id}/publish', 'Projects', 'Create project, my projects'),
  pending('POST', '/api/projects/{id}/close', 'Projects', 'My projects'),

  // ---- Proposals (pending)
  pending('POST', '/api/projects/{id}/proposals', 'Proposals', 'Submit proposal'),
  pending('GET', '/api/projects/{id}/proposals', 'Proposals', 'Proposal review (owner only)'),
  pending('GET', '/api/proposals/mine', 'Proposals', 'My proposals, freelancer dashboard'),
  pending('POST', '/api/proposals/{id}/shortlist', 'Proposals', 'Proposal review'),
  pending('POST', '/api/proposals/{id}/accept', 'Proposals', 'Proposal review, negotiation'),
  pending('POST', '/api/proposals/{id}/reject', 'Proposals', 'Proposal review, negotiation'),

  // ---- Negotiation (pending)
  pending(
    'GET',
    '/api/proposals/{id}/negotiation',
    'Negotiation',
    'Negotiation workspace',
    'Response envelope { proposal, messages, agreement? } is an assumption.',
  ),
  pending('POST', '/api/proposals/{id}/negotiation', 'Negotiation', 'Negotiation workspace'),
  pending('POST', '/api/agreements/{id}/counter', 'Negotiation', 'Negotiation workspace, agreement page'),
  pending('POST', '/api/agreements/{id}/finalize', 'Negotiation', 'Agreement page'),

  // ---- Agreements (pending)
  pending('GET', '/api/agreements/mine', 'Agreements', 'Agreements list, freelancer dashboard'),
  pending('GET', '/api/agreements/{id}', 'Agreements', 'Agreement details'),
  pending('GET', '/api/agreements/{id}/milestones', 'Agreements', 'Agreement details'),

  // ---- Not used by any page yet (future increments)
  pending('POST', '/api/agreements/{id}/escrow/register', 'Escrow', 'Future: fund escrow'),
  pending('POST', '/api/transactions/verify', 'Escrow', 'Future: verify a transaction'),
  pending('GET', '/api/transactions/mine', 'Escrow', 'Future: transaction history'),
  pending('POST', '/api/agreements/{id}/reconcile', 'Escrow', 'Future: reconcile with chain'),
  pending('POST', '/api/milestones/{id}/start', 'Milestones', 'Future: milestone pages'),
  pending('POST', '/api/milestones/{id}/submissions', 'Submissions', 'Future: submit work'),
  pending('POST', '/api/submissions/{id}/approve', 'Submissions', 'Future: approve work'),
  pending('POST', '/api/submissions/{id}/request-revision', 'Submissions', 'Future: request revision'),
  pending('POST', '/api/milestones/{id}/extensions', 'Deadlines', 'Future: deadline extensions'),
  pending('POST', '/api/milestones/{id}/settlements', 'Settlement', 'Future: settlements'),
  pending('POST', '/api/milestones/{id}/disputes', 'Disputes', 'Future: open dispute'),
  pending('GET', '/api/disputes/mine', 'Disputes', 'Future: disputes list'),
  pending('GET', '/api/admin/disputes', 'Admin', 'Future: admin dispute queue', 'Path is reserved for ROLE_ADMIN in SecurityConfig.'),
  pending('POST', '/api/agreements/{id}/reviews', 'Reviews', 'Future: reviews'),
];

export function endpointCounts(): { live: number; pending: number } {
  let liveCount = 0;
  for (const e of ENDPOINTS) if (e.status === 'live') liveCount += 1;
  return { live: liveCount, pending: ENDPOINTS.length - liveCount };
}
