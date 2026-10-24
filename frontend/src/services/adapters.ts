import type {
  Agreement,
  AgreementMilestone,
  AgreementStatus,
  MilestonePlanItem,
  NegotiationMessage,
  NegotiationThread,
  Project,
  ProjectInput,
  ProjectStatus,
  ProposedTerms,
  Proposal,
  ProposalStatus,
} from '../types/marketplace';
import { parseSkills, splitList } from '../utils/validation';

/**
 * Adapters turn whatever the (not yet written) controllers return into the shapes the UI uses.
 * They are deliberately tolerant about envelopes and a few likely field names, and strict about nothing else:
 * when the real DTOs exist, tighten them here and the pages stay untouched.
 */
type Raw = Record<string, unknown>;

export function asRaw(value: unknown): Raw {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Raw) : {};
}

function str(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : value == null ? fallback : String(value);
}

function optStr(value: unknown): string | undefined {
  return typeof value === 'string' && value !== '' ? value : undefined;
}

function num(value: unknown, fallback = 0): number {
  if (typeof value === 'number') return Number.isFinite(value) ? value : fallback;
  if (typeof value === 'string' && value.trim() !== '') {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
  }
  return fallback;
}

function optNum(value: unknown): number | undefined {
  if (value == null || value === '') return undefined;
  const n = num(value, Number.NaN);
  return Number.isNaN(n) ? undefined : n;
}

/** wei columns are DECIMAL(38,0): keep them as strings, never JS numbers. */
function optWei(value: unknown): string | undefined {
  if (typeof value === 'string' && /^\d+$/.test(value)) return value;
  if (typeof value === 'number' && Number.isInteger(value) && value >= 0) return BigInt(value).toString();
  return undefined;
}

function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

/** Accept a bare array or a Spring Page ({ content: [...] }). */
export function unwrapList(data: unknown): unknown[] {
  if (Array.isArray(data)) return data;
  const raw = asRaw(data);
  if (Array.isArray(raw.content)) return raw.content;
  if (Array.isArray(raw.items)) return raw.items;
  return [];
}

export function adaptMilestonePlan(value: unknown): MilestonePlanItem[] {
  return list(value)
    .map((item, index) => {
      const r = asRaw(item);
      return {
        position: num(r.position, index + 1),
        description: str(r.description),
        allocationInr: num(r.allocationInr ?? r.allocation),
      };
    })
    .sort((a, b) => a.position - b.position);
}

export function adaptProject(data: unknown): Project {
  const r = asRaw(data);
  const client = asRaw(r.client);
  return {
    id: num(r.id),
    clientId: num(r.clientId ?? client.id),
    clientName: optStr(r.clientName ?? client.displayName),
    title: str(r.title),
    description: str(r.description),
    category: str(r.category),
    skills: parseSkills(r.skills),
    budgetInr: num(r.budgetInr ?? r.budget),
    deadline: optStr(r.deadline) ?? null,
    status: str(r.status, 'DRAFT') as ProjectStatus,
    milestones: adaptMilestonePlan(r.milestones ?? r.milestonePlan ?? r.milestonePlans),
    createdAt: str(r.createdAt),
    updatedAt: optStr(r.updatedAt),
  };
}

/** UI -> request body. `skills` is sent as one comma separated string because that is how the column stores it. */
export function projectToPayload(input: ProjectInput): Record<string, unknown> {
  return {
    title: input.title.trim(),
    description: input.description.trim(),
    category: input.category.trim(),
    skills: input.skills.join(', '),
    budgetInr: input.budgetInr,
    deadline: input.deadline ?? null,
    milestones: input.milestones.map((m, i) => ({
      position: i + 1,
      description: m.description.trim(),
      allocationInr: m.allocationInr,
    })),
  };
}

export function adaptProposal(data: unknown): Proposal {
  const r = asRaw(data);
  const project = asRaw(r.project);
  const freelancer = asRaw(r.freelancer);
  const links = r.portfolioLinks;
  return {
    id: num(r.id),
    projectId: num(r.projectId ?? project.id),
    projectTitle: optStr(r.projectTitle ?? project.title),
    freelancerId: num(r.freelancerId ?? freelancer.id),
    freelancerName: optStr(r.freelancerName ?? freelancer.displayName),
    message: str(r.message),
    priceInr: num(r.priceInr ?? r.price),
    durationDays: num(r.durationDays),
    portfolioLinks: Array.isArray(links)
      ? links.filter((l): l is string => typeof l === 'string')
      : typeof links === 'string'
        ? splitList(links)
        : [],
    status: str(r.status, 'SUBMITTED') as ProposalStatus,
    createdAt: str(r.createdAt),
    updatedAt: optStr(r.updatedAt),
  };
}

function adaptTerms(value: unknown): ProposedTerms | null {
  const r = asRaw(value);
  const priceInr = optNum(r.priceInr ?? r.price);
  const durationDays = optNum(r.durationDays);
  if (priceInr === undefined && durationDays === undefined) return null;
  return { priceInr, durationDays };
}

export function adaptMessage(data: unknown): NegotiationMessage {
  const r = asRaw(data);
  const sender = asRaw(r.sender);
  return {
    id: num(r.id),
    proposalId: num(r.proposalId),
    senderId: num(r.senderId ?? sender.id),
    senderName: optStr(r.senderName ?? sender.displayName),
    message: str(r.message),
    proposedTerms: adaptTerms(r.proposedTerms ?? r.proposedTermsJson ?? r.terms),
    createdAt: str(r.createdAt),
  };
}

export function adaptAgreementMilestone(data: unknown, index = 0): AgreementMilestone {
  const r = asRaw(data);
  return {
    id: optNum(r.id),
    position: num(r.position, index + 1),
    description: str(r.description),
    acceptanceCriteria: optStr(r.acceptanceCriteria) ?? null,
    amountWei: optWei(r.amountWei) ?? null,
    amountInr: optNum(r.amountInr) ?? null,
    effectiveDeadline: optStr(r.effectiveDeadline ?? r.deadline) ?? null,
    workStatus: optStr(r.workStatus),
    chainStatus: optStr(r.chainStatus),
  };
}

export function adaptAgreement(data: unknown): Agreement {
  const r = asRaw(data);
  const snapshotRaw = r.snapshot ?? r.snapshotJson;
  const snapshot = typeof snapshotRaw === 'object' && snapshotRaw !== null ? asRaw(snapshotRaw) : null;
  const snap = snapshot ?? {};
  return {
    id: num(r.id),
    proposalId: num(r.proposalId),
    projectId: optNum(r.projectId ?? snap.projectId),
    projectTitle: optStr(r.projectTitle ?? snap.projectTitle ?? snap.title),
    projectDescription: optStr(r.projectDescription ?? snap.projectDescription ?? snap.description),
    clientId: optNum(r.clientId),
    clientName: optStr(r.clientName ?? snap.clientName),
    freelancerId: optNum(r.freelancerId),
    freelancerName: optStr(r.freelancerName ?? snap.freelancerName),
    totalWei: optWei(r.totalWei) ?? null,
    priceInr: optNum(r.priceInr ?? snap.priceInr ?? snap.price) ?? null,
    deadline: optStr(r.deadline ?? snap.deadline) ?? null,
    status: str(r.status, 'DRAFT') as AgreementStatus,
    clientFinalizedAt: optStr(r.clientFinalizedAt) ?? null,
    freelancerFinalizedAt: optStr(r.freelancerFinalizedAt) ?? null,
    snapshot,
    snapshotHash: optStr(r.snapshotHash) ?? null,
    clientWallet: optStr(r.clientWallet) ?? null,
    freelancerWallet: optStr(r.freelancerWallet) ?? null,
    escrowAddress: optStr(r.escrowAddress) ?? null,
    chainId: optNum(r.chainId) ?? null,
    milestones: list(r.milestones).map((m, i) => adaptAgreementMilestone(m, i)),
    createdAt: optStr(r.createdAt),
  };
}

/** ASSUMED envelope: { proposal, messages, agreement? }. Verify against the real DTO when it exists. */
export function adaptThread(data: unknown): NegotiationThread {
  const r = asRaw(data);
  const agreement = r.agreement == null ? null : adaptAgreement(r.agreement);
  return {
    proposal: adaptProposal(r.proposal),
    messages: list(r.messages).map(adaptMessage),
    agreement,
  };
}

export function termsToPayload(terms?: ProposedTerms): Record<string, unknown> | undefined {
  if (!terms) return undefined;
  const out: Record<string, unknown> = {};
  if (terms.priceInr !== undefined) out.priceInr = terms.priceInr;
  if (terms.durationDays !== undefined) out.durationDays = terms.durationDays;
  return Object.keys(out).length ? out : undefined;
}
