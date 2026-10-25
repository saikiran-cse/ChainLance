import { env } from '../config/env';
import { mockAgreementsApi } from '../mocks/mockApis';
import type { Agreement, AgreementMilestone } from '../types/marketplace';
import { guard } from '../utils/errors';
import { adaptAgreement, adaptAgreementMilestone, unwrapList } from './adapters';
import { api } from './api';
import type { AgreementsApi } from './contracts';

/**
 * LIVE implementation of Agreements API against Spring Boot backend.
 */
const liveAgreementsApi: AgreementsApi = {
  async mine(): Promise<Agreement[]> {
    const { data } = await guard(api.get<unknown>('/api/agreements/my'));
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
};

export const agreementsService: AgreementsApi = env.useMocks ? mockAgreementsApi : liveAgreementsApi;
