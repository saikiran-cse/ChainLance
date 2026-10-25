import { env } from '../config/env';
import { mockProjectsApi } from '../mocks/mockApis';
import type { PageResponse, Project, ProjectInput, ProjectQuery } from '../types/marketplace';
import { guard } from '../utils/errors';
import { adaptPage, adaptProject, projectToPayload, unwrapList } from './adapters';
import { api } from './api';
import type { ProjectsApi } from './contracts';

/**
 * LIVE implementation of Projects API against Spring Boot backend.
 */
const liveProjectsApi: ProjectsApi = {
  async list(query?: ProjectQuery, page = 0, size = 20): Promise<PageResponse<Project>> {
    const params: Record<string, string | number> = { page, size };
    if (query?.category) params.category = query.category;
    const { data } = await guard(api.get<unknown>('/api/projects', { params }));
    const paged = adaptPage(data, adaptProject);
    if (query?.q || query?.skill) {
      const q = query.q?.toLowerCase().trim();
      const skill = query.skill?.toLowerCase().trim();
      const filtered = paged.content.filter((p) => {
        const matchesQ = !q || p.title.toLowerCase().includes(q) || p.description.toLowerCase().includes(q);
        const matchesSkill = !skill || p.skills.some((s) => s.toLowerCase().includes(skill));
        return matchesQ && matchesSkill;
      });
      return {
        ...paged,
        content: filtered,
        totalElements: filtered.length,
      };
    }
    return paged;
  },

  async get(id: number): Promise<Project> {
    const { data } = await guard(api.get<unknown>(`/api/projects/${id}`));
    return adaptProject(data);
  },

  async mine(): Promise<Project[]> {
    const { data } = await guard(api.get<unknown>('/api/projects/mine'));
    return unwrapList(data).map(adaptProject);
  },

  async create(input: ProjectInput, publish: boolean): Promise<Project> {
    const { data } = await guard(api.post<unknown>('/api/projects', projectToPayload(input)));
    const created = adaptProject(data);
    return publish ? liveProjectsApi.publish(created.id) : created;
  },

  async update(id: number, input: ProjectInput): Promise<Project> {
    const { data } = await guard(api.put<unknown>(`/api/projects/${id}`, projectToPayload(input)));
    return adaptProject(data);
  },

  async publish(id: number): Promise<Project> {
    const { data } = await guard(api.post<unknown>(`/api/projects/${id}/publish`));
    return adaptProject(data);
  },

  async close(id: number): Promise<Project> {
    const { data } = await guard(api.post<unknown>(`/api/projects/${id}/close`));
    return adaptProject(data);
  },

  async cancel(id: number): Promise<Project> {
    const { data } = await guard(api.post<unknown>(`/api/projects/${id}/cancel`));
    return adaptProject(data);
  },
};

/** Pages import this. Set VITE_USE_MOCKS=true to use labelled sample data. */
export const projectsService: ProjectsApi = env.useMocks ? mockProjectsApi : liveProjectsApi;
