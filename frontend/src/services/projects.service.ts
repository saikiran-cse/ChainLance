import { env } from '../config/env';
import { mockProjectsApi } from '../mocks/mockApis';
import type { Project, ProjectInput, ProjectQuery } from '../types/marketplace';
import { guard } from '../utils/errors';
import { adaptProject, projectToPayload, unwrapList } from './adapters';
import { api } from './api';
import type { ProjectsApi } from './contracts';
import { getServiceIdentity } from './identity';

/**
 * LIVE implementation. PENDING: none of these endpoints exist in the backend yet (see services/endpoints.ts).
 * Paths follow ARCHITECTURE.md section 4; request and response bodies are assumptions isolated in services/adapters.ts.
 */
const liveProjectsApi: ProjectsApi = {
  async list(query: ProjectQuery): Promise<Project[]> {
    const params: Record<string, string> = {};
    if (query.q?.trim()) params.q = query.q.trim();
    if (query.category) params.category = query.category;
    if (query.skill) params.skill = query.skill;
    const { data } = await guard(api.get<unknown>('/api/projects', { params }));
    return unwrapList(data).map(adaptProject);
  },

  async get(id: number): Promise<Project> {
    const { data } = await guard(api.get<unknown>(`/api/projects/${id}`));
    return adaptProject(data);
  },

  /**
   * ASSUMPTION: no "my projects" endpoint is documented. This asks GET /api/projects for the caller's own projects
   * with ?mine=true and, in case the parameter is ignored, also filters by clientId so a client never sees others'.
   */
  async mine(): Promise<Project[]> {
    const { data } = await guard(api.get<unknown>('/api/projects', { params: { mine: true } }));
    const me = getServiceIdentity();
    const all = unwrapList(data).map(adaptProject);
    return me ? all.filter((p) => p.clientId === me.id) : all;
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
};

/** Pages import this. Set VITE_USE_MOCKS=true to use labelled sample data instead of the (missing) endpoints. */
export const projectsService: ProjectsApi = env.useMocks ? mockProjectsApi : liveProjectsApi;
