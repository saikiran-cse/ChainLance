import { env } from '../config/env';
import { mockNegotiationApi } from '../mocks/mockApis';
import type { Agreement, NegotiationMessage, NegotiationThread, Offer, ProposedTerms } from '../types/marketplace';
import { guard } from '../utils/errors';
import { adaptOffer, unwrapList } from './adapters';
import { agreementsService } from './agreements.service';
import { api } from './api';
import type { NegotiationApi } from './contracts';
import { proposalsService } from './proposals.service';

/** LIVE implementation of Negotiation API against Spring Boot backend. */
const liveNegotiationApi: NegotiationApi = {
  async offers(proposalId: number): Promise<Offer[]> {
    const { data } = await guard(api.get<unknown>(`/api/proposals/${proposalId}/offers`));
    return unwrapList(data).map(adaptOffer);
  },

  async makeOffer(
    proposalId: number,
    input: { priceInr: number; durationDays: number; note?: string },
  ): Promise<Offer> {
    const { data } = await guard(
      api.post<unknown>(`/api/proposals/${proposalId}/offers`, {
        priceInr: input.priceInr,
        durationDays: input.durationDays,
        note: input.note?.trim() || undefined,
      }),
    );
    return adaptOffer(data);
  },

  async acceptOffer(proposalId: number, offerId: number): Promise<Offer> {
    const { data } = await guard(api.post<unknown>(`/api/proposals/${proposalId}/offers/${offerId}/accept`));
    return adaptOffer(data);
  },

  async counterOffer(
    proposalId: number,
    offerId: number,
    input: { priceInr: number; durationDays: number; note?: string },
  ): Promise<Offer> {
    const { data } = await guard(
      api.post<unknown>(`/api/proposals/${proposalId}/offers/${offerId}/counter`, {
        priceInr: input.priceInr,
        durationDays: input.durationDays,
        note: input.note?.trim() || undefined,
      }),
    );
    return adaptOffer(data);
  },

  async withdrawOffer(proposalId: number, offerId: number): Promise<Offer> {
    const { data } = await guard(api.post<unknown>(`/api/proposals/${proposalId}/offers/${offerId}/withdraw`));
    return adaptOffer(data);
  },

  async thread(proposalId: number): Promise<NegotiationThread> {
    const proposal = await proposalsService.get(proposalId);
    let agreement: Agreement | null = null;
    try {
      const agreements = await agreementsService.mine();
      agreement = agreements.find((a) => a.proposalId === proposalId) ?? null;
    } catch {
      agreement = null;
    }
    const offers = proposal.offers && proposal.offers.length > 0 ? proposal.offers : await liveNegotiationApi.offers(proposalId);
    const messages: NegotiationMessage[] = offers.map((o) => ({
      id: o.id,
      proposalId: o.proposalId,
      senderId: o.offeredBy,
      senderName: o.offeredBy === proposal.freelancerId ? proposal.freelancerName : 'Client',
      message: o.note || (o.status === 'ACCEPTED' ? 'Terms accepted.' : `Offer: ₹${o.priceInr} in ${o.durationDays} days`),
      proposedTerms: { priceInr: o.priceInr, durationDays: o.durationDays },
      createdAt: o.createdAt,
    }));
    return {
      proposal: { ...proposal, offers },
      messages,
      agreement,
    };
  },

  async send(
    proposalId: number,
    body: { message: string; terms?: ProposedTerms },
  ): Promise<Offer | NegotiationMessage> {
    const proposal = await proposalsService.get(proposalId);
    const offers = proposal.offers && proposal.offers.length > 0 ? proposal.offers : await liveNegotiationApi.offers(proposalId);
    const pendingOffer = offers.find((o) => o.status === 'PENDING');
    const priceInr = body.terms?.priceInr ?? proposal.priceInr;
    const durationDays = body.terms?.durationDays ?? proposal.durationDays;
    const note = body.message.trim() || undefined;

    if (pendingOffer) {
      return await liveNegotiationApi.counterOffer(proposalId, pendingOffer.id, {
        priceInr,
        durationDays,
        note,
      });
    } else {
      return await liveNegotiationApi.makeOffer(proposalId, {
        priceInr,
        durationDays,
        note,
      });
    }
  },
};

export const negotiationService: NegotiationApi = env.useMocks ? mockNegotiationApi : liveNegotiationApi;
