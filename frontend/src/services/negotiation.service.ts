import { env } from '../config/env';
import { mockNegotiationApi } from '../mocks/mockApis';
import type { NegotiationMessage, NegotiationThread, ProposedTerms } from '../types/marketplace';
import { guard } from '../utils/errors';
import { adaptMessage, adaptThread, termsToPayload } from './adapters';
import { api } from './api';
import type { NegotiationApi } from './contracts';

/** LIVE implementation. PENDING: neither endpoint exists in the backend yet. */
const liveNegotiationApi: NegotiationApi = {
  async thread(proposalId: number): Promise<NegotiationThread> {
    const { data } = await guard(api.get<unknown>(`/api/proposals/${proposalId}/negotiation`));
    return adaptThread(data);
  },

  async send(
    proposalId: number,
    body: { message: string; terms?: ProposedTerms },
  ): Promise<NegotiationMessage> {
    const { data } = await guard(
      api.post<unknown>(`/api/proposals/${proposalId}/negotiation`, {
        message: body.message.trim(),
        proposedTerms: termsToPayload(body.terms),
      }),
    );
    return adaptMessage(data);
  },
};

export const negotiationService: NegotiationApi = env.useMocks ? mockNegotiationApi : liveNegotiationApi;
