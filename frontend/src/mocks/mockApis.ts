/**
 * SAMPLE-DATA implementations of the service contracts. Used only when VITE_USE_MOCKS=true.
 * They enforce a few basic rules (ownership, duplicates, roles) so the UI can be exercised end to end,
 * but they are NOT a model of the real backend's rules, which are still being written.
 */
import { getServiceIdentity } from '../services/identity';
import type {
  AgreementsApi,
  NegotiationApi,
  ProjectsApi,
  ProposalsApi,
} from '../services/contracts';
import type {
  Agreement,
  NegotiationMessage,
  Project,
  Proposal,
} from '../types/marketplace';
import { AppError } from '../utils/errors';
import {
  buildAgreement,
  conflict,
  delay,
  ensureAnonymousSeed,
  ensureSeed,
  forbidden,
  iso,
  nextId,
  notFound,
  requireIdentity,
  state,
} from './mockDb';

function clone<T>(value: T): T {
  return structuredClone(value);
}

function newestFirst<T extends { createdAt?: string }>(items: T[]): T[] {
  return [...items].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
}

function projectById(id: number): Project {
  const found = state.projects.find((p) => p.id === id);
  if (!found) throw notFound('That project');
  return found;
}

function proposalById(id: number): Proposal {
  const found = state.proposals.find((p) => p.id === id);
  if (!found) throw notFound('That proposal');
  return found;
}

function agreementById(id: number): Agreement {
  const found = state.agreements.find((a) => a.id === id);
  if (!found) throw notFound('That agreement');
  return found;
}

function agreementForProposal(proposalId: number): Agreement | null {
  return state.agreements.find((a) => a.proposalId === proposalId) ?? null;
}

function ownerOf(proposal: Proposal): number {
  return projectById(proposal.projectId).clientId;
}

function isParticipant(proposal: Proposal, userId: number): boolean {
  return proposal.freelancerId === userId || ownerOf(proposal) === userId;
}

/* -------------------------------------------------------------------------------------------------------- projects */
export const mockProjectsApi: ProjectsApi = {
  async list(query) {
    await delay();
    const me = getServiceIdentity();
    if (me) ensureSeed(me);
    else ensureAnonymousSeed();
    const q = query.q?.trim().toLowerCase();
    return clone(
      newestFirst(
        state.projects.filter((p) => {
          if (p.status !== 'OPEN') return false;
          if (query.category && p.category !== query.category) return false;
          if (query.skill && !p.skills.some((s) => s.toLowerCase() === query.skill?.toLowerCase())) return false;
          if (q) {
            const haystack = `${p.title} ${p.description} ${p.skills.join(' ')} ${p.clientName ?? ''}`.toLowerCase();
            if (!haystack.includes(q)) return false;
          }
          return true;
        }),
      ),
    );
  },

  async get(id) {
    await delay();
    const me = getServiceIdentity();
    if (me) ensureSeed(me);
    else ensureAnonymousSeed();
    const found = projectById(id);
    if (found.status !== 'OPEN' && found.clientId !== me?.id) {
      // Non-public projects are only visible to their owner (and to the freelancer working on them in the real app).
      const involved = me && state.proposals.some((p) => p.projectId === id && p.freelancerId === me.id);
      if (!involved) throw notFound('That project');
    }
    return clone(found);
  },

  async mine() {
    await delay();
    const me = requireIdentity();
    if (me.role !== 'CLIENT') throw forbidden('Only clients have projects.');
    return clone(newestFirst(state.projects.filter((p) => p.clientId === me.id)));
  },

  async create(input, publish) {
    await delay(350);
    const me = requireIdentity();
    if (me.role !== 'CLIENT') throw forbidden('Only clients can create projects.');
    const created: Project = {
      id: nextId(),
      clientId: me.id,
      clientName: me.displayName,
      title: input.title.trim(),
      description: input.description.trim(),
      category: input.category,
      skills: input.skills,
      budgetInr: input.budgetInr,
      deadline: input.deadline ?? null,
      status: publish ? 'OPEN' : 'DRAFT',
      milestones: input.milestones.map((m, i) => ({ ...m, position: i + 1 })),
      createdAt: iso(0),
      updatedAt: iso(0),
    };
    state.projects.push(created);
    return clone(created);
  },

  async update(id, input) {
    await delay(350);
    const me = requireIdentity();
    const found = projectById(id);
    if (found.clientId !== me.id) throw forbidden();
    Object.assign(found, {
      title: input.title.trim(),
      description: input.description.trim(),
      category: input.category,
      skills: input.skills,
      budgetInr: input.budgetInr,
      deadline: input.deadline ?? null,
      milestones: input.milestones.map((m, i) => ({ ...m, position: i + 1 })),
      updatedAt: iso(0),
    });
    return clone(found);
  },

  async publish(id) {
    await delay();
    const me = requireIdentity();
    const found = projectById(id);
    if (found.clientId !== me.id) throw forbidden();
    if (found.status !== 'DRAFT') throw conflict('Only draft projects can be published.', 'PROJECT_NOT_DRAFT');
    found.status = 'OPEN';
    found.updatedAt = iso(0);
    return clone(found);
  },

  async close(id) {
    await delay();
    const me = requireIdentity();
    const found = projectById(id);
    if (found.clientId !== me.id) throw forbidden();
    if (found.status !== 'OPEN' && found.status !== 'DRAFT') {
      throw conflict('Only open or draft projects can be closed.', 'PROJECT_NOT_CLOSABLE');
    }
    found.status = 'CLOSED';
    found.updatedAt = iso(0);
    return clone(found);
  },
};

/* ------------------------------------------------------------------------------------------------------ proposals */
export const mockProposalsApi: ProposalsApi = {
  async submit(projectId, input) {
    await delay(350);
    const me = requireIdentity();
    if (me.role !== 'FREELANCER') throw forbidden('Only freelancers can submit proposals.');
    const target = projectById(projectId);
    if (target.status !== 'OPEN') throw conflict('This project is not open for proposals.', 'PROJECT_NOT_OPEN');
    if (state.proposals.some((p) => p.projectId === projectId && p.freelancerId === me.id)) {
      throw conflict('You have already sent a proposal for this project.', 'PROPOSAL_EXISTS');
    }
    const created: Proposal = {
      id: nextId(),
      projectId,
      projectTitle: target.title,
      freelancerId: me.id,
      freelancerName: me.displayName,
      message: input.message.trim(),
      priceInr: input.priceInr,
      durationDays: input.durationDays,
      portfolioLinks: input.portfolioLinks,
      status: 'SUBMITTED',
      createdAt: iso(0),
    };
    state.proposals.push(created);
    return clone(created);
  },

  async forProject(projectId) {
    await delay();
    const me = requireIdentity();
    if (projectById(projectId).clientId !== me.id) throw forbidden('Only the project owner can see its proposals.');
    return clone(newestFirst(state.proposals.filter((p) => p.projectId === projectId)));
  },

  async mine() {
    await delay();
    const me = requireIdentity();
    return clone(newestFirst(state.proposals.filter((p) => p.freelancerId === me.id)));
  },

  async shortlist(id) {
    await delay();
    const me = requireIdentity();
    const found = proposalById(id);
    if (ownerOf(found) !== me.id) throw forbidden();
    if (found.status !== 'SUBMITTED') throw conflict('Only new proposals can be shortlisted.', 'PROPOSAL_STATE');
    found.status = 'SHORTLISTED';
    return clone(found);
  },

  async accept(id) {
    await delay(350);
    const me = requireIdentity();
    const found = proposalById(id);
    if (ownerOf(found) !== me.id) throw forbidden();
    if (found.status !== 'SUBMITTED' && found.status !== 'SHORTLISTED') {
      throw conflict('This proposal can no longer be accepted.', 'PROPOSAL_STATE');
    }
    found.status = 'ACCEPTED';
    if (!agreementForProposal(found.id)) {
      const proj = projectById(found.projectId);
      const lastTerms = [...state.messages]
        .filter((m) => m.proposalId === found.id && m.proposedTerms)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]?.proposedTerms;
      state.agreements.push(
        buildAgreement(
          found,
          proj,
          lastTerms?.priceInr ?? found.priceInr,
          lastTerms?.durationDays ?? found.durationDays,
        ),
      );
    }
    return clone(found);
  },

  async reject(id) {
    await delay();
    const me = requireIdentity();
    const found = proposalById(id);
    if (ownerOf(found) !== me.id) throw forbidden();
    if (found.status === 'ACCEPTED') throw conflict('An accepted proposal cannot be rejected.', 'PROPOSAL_STATE');
    found.status = 'REJECTED';
    return clone(found);
  },
};

/* ---------------------------------------------------------------------------------------------------- negotiation */
function appendMessage(
  proposal: Proposal,
  senderId: number,
  senderName: string,
  text: string,
  terms?: { priceInr?: number; durationDays?: number },
): NegotiationMessage {
  const created: NegotiationMessage = {
    id: nextId(),
    proposalId: proposal.id,
    senderId,
    senderName,
    message: text.trim(),
    proposedTerms: terms && (terms.priceInr !== undefined || terms.durationDays !== undefined) ? terms : null,
    createdAt: iso(0),
  };
  state.messages.push(created);
  return created;
}

export const mockNegotiationApi: NegotiationApi = {
  async thread(proposalId) {
    await delay();
    const me = requireIdentity();
    const proposal = proposalById(proposalId);
    if (!isParticipant(proposal, me.id)) throw forbidden('You are not part of this negotiation.');
    return clone({
      proposal,
      messages: state.messages
        .filter((m) => m.proposalId === proposalId)
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
      agreement: agreementForProposal(proposalId),
    });
  },

  async send(proposalId, body) {
    await delay(300);
    const me = requireIdentity();
    const proposal = proposalById(proposalId);
    if (!isParticipant(proposal, me.id)) throw forbidden('You are not part of this negotiation.');
    if (proposal.status === 'REJECTED' || proposal.status === 'WITHDRAWN') {
      throw conflict('This negotiation is closed.', 'NEGOTIATION_CLOSED');
    }
    if (!body.message.trim() && !body.terms) {
      throw new AppError('Write a message or propose new terms.', { kind: 'validation', status: 400 });
    }
    return clone(appendMessage(proposal, me.id, me.displayName, body.message, body.terms));
  },
};

/* ----------------------------------------------------------------------------------------------------- agreements */
function requireAgreementParticipant(agreement: Agreement, userId: number): 'client' | 'freelancer' {
  if (agreement.clientId === userId) return 'client';
  if (agreement.freelancerId === userId) return 'freelancer';
  throw forbidden('You are not a party to this agreement.');
}

export const mockAgreementsApi: AgreementsApi = {
  async mine() {
    await delay();
    const me = requireIdentity();
    return clone(
      newestFirst(state.agreements.filter((a) => a.clientId === me.id || a.freelancerId === me.id)),
    );
  },

  async get(id) {
    await delay();
    const me = requireIdentity();
    const agreement = agreementById(id);
    requireAgreementParticipant(agreement, me.id);
    return clone(agreement);
  },

  async milestones(id) {
    await delay();
    const me = requireIdentity();
    const agreement = agreementById(id);
    requireAgreementParticipant(agreement, me.id);
    return clone(agreement.milestones);
  },

  async counter(id, body) {
    await delay(350);
    const me = requireIdentity();
    const agreement = agreementById(id);
    requireAgreementParticipant(agreement, me.id);
    if (agreement.status !== 'DRAFT') {
      throw conflict('Only a draft agreement can be countered.', 'AGREEMENT_NOT_DRAFT');
    }
    const proposal = proposalById(agreement.proposalId);
    appendMessage(proposal, me.id, me.displayName, body.message, body.terms);
    if (body.terms?.priceInr !== undefined) {
      agreement.priceInr = body.terms.priceInr;
      agreement.snapshot = { ...(agreement.snapshot ?? {}), priceInr: body.terms.priceInr };
    }
    if (body.terms?.durationDays !== undefined) {
      agreement.snapshot = { ...(agreement.snapshot ?? {}), durationDays: body.terms.durationDays };
      agreement.deadline = iso(body.terms.durationDays);
    }
    // Changing terms means both sides must confirm again.
    agreement.clientFinalizedAt = null;
    agreement.freelancerFinalizedAt = null;
    return clone(agreement);
  },

  async finalize(id) {
    await delay(350);
    const me = requireIdentity();
    const agreement = agreementById(id);
    const side = requireAgreementParticipant(agreement, me.id);
    if (agreement.status !== 'DRAFT') throw conflict('This agreement is already finalized.', 'AGREEMENT_NOT_DRAFT');
    if (side === 'client') agreement.clientFinalizedAt = iso(0);
    else agreement.freelancerFinalizedAt = iso(0);
    if (agreement.clientFinalizedAt && agreement.freelancerFinalizedAt) agreement.status = 'FINALIZED';
    return clone(agreement);
  },
};
