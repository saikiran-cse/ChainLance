import type { AuthResponse, LoginPayload, RegisterPayload, UpdateProfilePayload, User } from '../types/api';
import { guard } from '../utils/errors';
import { api } from './api';

/** LIVE: all four endpoints exist in the backend (AuthController, UserController). */
export const authService = {
  async register(payload: RegisterPayload): Promise<AuthResponse> {
    const { data } = await guard(api.post<AuthResponse>('/api/auth/register', payload));
    return data;
  },

  async login(payload: LoginPayload): Promise<AuthResponse> {
    const { data } = await guard(api.post<AuthResponse>('/api/auth/login', payload));
    return data;
  },

  async me(): Promise<User> {
    const { data } = await guard(api.get<User>('/api/users/me'));
    return data;
  },

  async updateProfile(payload: UpdateProfilePayload): Promise<User> {
    const { data } = await guard(api.put<User>('/api/users/me', payload));
    return data;
  },
};
