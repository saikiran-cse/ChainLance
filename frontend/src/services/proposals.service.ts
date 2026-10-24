import { env } from '../config/env';
import { mockProposalsApi } from '../mocks/mockApis';
import type { Proposal, ProposalInput } from '../types/marketplace';
import { guard } from '../utils/errors';
import { adaptProposal, unwrapList } from './adapters';
import { api } from './api';
import type { ProposalsApi } from './contracts';

/** LIVE implementation. PENDING: none of these endpoints exist in the backend yet. */
const liveProposalsApi: ProposalsApi = {
  async submit(projectId: number, input: ProposalInput): Promise<Proposal> {
    const { data } = await guard(
      api.post<unknown>(`/api/projects/${projectId}/proposals`, {
        message: input.message.trim(),
        priceInr: input.priceInr,
        durationDays: input.durationDays,
        // The column is one VARCHAR(1000); URLs may contain commas, so entries are newline separated.
        portfolioLinks: input.portfolioLinks.join('\n'),
      }),
    );
    return adaptProposal(data);
  },

  async forProject(projectId: number): Promise<Proposal[]> {
    const { data } = await guard(api.get<unknown>(`/api/projects/${projectId}/proposals`));
    return unwrapList(data).map(adaptProposal);
  },

  async mine(): Promise<Proposal[]> {
    const { data } = await guard(api.get<unknown>('/api/proposals/mine'));
    return unwrapList(data).map(adaptProposal);
  },

  async shortlist(id: number): Promise<Proposal> {
    const { data } = await guard(api.post<unknown>(`/api/proposals/${id}/shortlist`));
    return adaptProposal(data);
  },

  async accept(id: number): Promise<Proposal> {
    const { data } = await guard(api.post<unknown>(`/api/proposals/${id}/accept`));
    return adaptProposal(data);
  },

  async reject(id: number): Promise<Proposal> {
    const { data } = await guard(api.post<unknown>(`/api/proposals/${id}/reject`));
    return adaptProposal(data);
  },
};

export const proposalsService: ProposalsApi = env.useMocks ? mockProposalsApi : liveProposalsApi;
