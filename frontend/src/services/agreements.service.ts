import { env } from '../config/env';
import { mockAgreementsApi } from '../mocks/mockApis';
import type { Agreement, AgreementMilestone, ProposedTerms } from '../types/marketplace';
import { guard } from '../utils/errors';
import { adaptAgreement, adaptAgreementMilestone, termsToPayload, unwrapList } from './adapters';
import { api } from './api';
import type { AgreementsApi } from './contracts';

/**
 * LIVE implementation. PENDING: none of these endpoints exist in the backend yet.
 * The UI never decides an agreement is final: it re-reads the status the backend returns after every action.
 */
const liveAgreementsApi: AgreementsApi = {
  async mine(): Promise<Agreement[]> {
    const { data } = await guard(api.get<unknown>('/api/agreements/mine'));
    return unwrapList(data).map(adaptAgreement);
  },

  async get(id: number): Promise<Agreement> {
    const { data } = await guard(api.get<unknown>(`/api/agreements/${id}`));
    return adaptAgreement(data);
  },

  async milestones(id: number): Promise<AgreementMilestone[]> {
    const { data } = await guard(api.get<unknown>(`/api/agreements/${id}/milestones`));
    return unwrapList(data).map((m, i) => adaptAgreementMilestone(m, i));
  },

  async counter(id: number, body: { message: string; terms?: ProposedTerms }): Promise<Agreement> {
    const { data } = await guard(
      api.post<unknown>(`/api/agreements/${id}/counter`, {
        message: body.message.trim(),
        proposedTerms: termsToPayload(body.terms),
      }),
    );
    return adaptAgreement(data);
  },

  async finalize(id: number): Promise<Agreement> {
    const { data } = await guard(api.post<unknown>(`/api/agreements/${id}/finalize`));
    return adaptAgreement(data);
  },
};

export const agreementsService: AgreementsApi = env.useMocks ? mockAgreementsApi : liveAgreementsApi;
