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
  async list(query = {}, page = 0, size = 20) {
    await delay();
    const me = getServiceIdentity();
    if (me) ensureSeed(me);
    else ensureAnonymousSeed();
    const q = query.q?.trim().toLowerCase();
    const filtered = newestFirst(
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
    );
    const start = page * size;
    const content = clone(filtered.slice(start, start + size));
    return {
      content,
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size) || 1,
    };
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

  async cancel(id) {
    await delay();
    const me = requireIdentity();
    const found = projectById(id);
    if (found.clientId !== me.id) throw forbidden();
    if (found.status !== 'OPEN' && found.status !== 'DRAFT') {
      throw conflict('Only open or draft projects can be cancelled.', 'PROJECT_NOT_CANCELLABLE');
    }
    found.status = 'CANCELLED';
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

  async get(id) {
    await delay();
    const me = requireIdentity();
    const found = proposalById(id);
    if (!isParticipant(found, me.id)) throw forbidden('You are not a participant in this proposal.');
    return clone(found);
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
    let agreement = agreementForProposal(found.id);
    if (!agreement) {
      const proj = projectById(found.projectId);
      const lastTerms = [...state.messages]
        .filter((m) => m.proposalId === found.id && m.proposedTerms)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]?.proposedTerms;
      agreement = buildAgreement(
        found,
        proj,
        lastTerms?.priceInr ?? found.priceInr,
        lastTerms?.durationDays ?? found.durationDays,
      );
      agreement.status = 'FINALIZED';
      agreement.snapshotIntegrityOk = true;
      state.agreements.push(agreement);
    }
    return clone(agreement);
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

  async withdraw(id) {
    await delay();
    const me = requireIdentity();
    const found = proposalById(id);
    if (found.freelancerId !== me.id) throw forbidden('Only the author can withdraw this proposal.');
    if (found.status !== 'SUBMITTED' && found.status !== 'SHORTLISTED') {
      throw conflict('This proposal can no longer be withdrawn.', 'PROPOSAL_STATE');
    }
    found.status = 'WITHDRAWN';
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
  async offers(proposalId) {
    await delay();
    const proposal = proposalById(proposalId);
    return clone(proposal.offers ?? []);
  },

  async makeOffer(proposalId, input) {
    await delay(300);
    const me = requireIdentity();
    const proposal = proposalById(proposalId);
    if (!proposal.offers) proposal.offers = [];
    const created = {
      id: nextId(),
      proposalId,
      offeredBy: me.id,
      priceInr: input.priceInr,
      durationDays: input.durationDays,
      note: input.note,
      status: 'PENDING' as const,
      createdAt: iso(0),
    };
    proposal.offers.push(created);
    appendMessage(proposal, me.id, me.displayName, input.note || `Offer ₹${input.priceInr}`, {
      priceInr: input.priceInr,
      durationDays: input.durationDays,
    });
    return clone(created);
  },

  async acceptOffer(proposalId, offerId) {
    await delay(300);
    const me = requireIdentity();
    const proposal = proposalById(proposalId);
    const offer = proposal.offers?.find((o) => o.id === offerId);
    if (!offer) throw notFound('Offer');
    offer.status = 'ACCEPTED';
    offer.respondedBy = me.id;
    offer.respondedAt = iso(0);
    return clone(offer);
  },

  async counterOffer(proposalId, offerId, input) {
    await delay(300);
    const proposal = proposalById(proposalId);
    const prev = proposal.offers?.find((o) => o.id === offerId);
    if (prev) prev.status = 'COUNTERED';
    return await mockNegotiationApi.makeOffer(proposalId, input);
  },

  async withdrawOffer(proposalId, offerId) {
    await delay(300);
    const proposal = proposalById(proposalId);
    const offer = proposal.offers?.find((o) => o.id === offerId);
    if (!offer) throw notFound('Offer');
    offer.status = 'WITHDRAWN';
    return clone(offer);
  },

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
};
