import axios from 'axios';
import { env } from '../config/env';
import { tokenStorage } from './tokenStorage';

/** Fired when the backend rejects a token the browser was holding (expired, revoked or user disabled). */
export const UNAUTHORIZED_EVENT = 'chainlance:unauthorized';

/** The one HTTP client. Every service uses it, so base URL, JWT attachment and 401 handling live here only. */
export const api = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 60_000,
  headers: { Accept: 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = tokenStorage.getToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

function isAuthEndpoint(url?: string): boolean {
  return typeof url === 'string' && url.includes('/api/auth/');
}

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      !isAuthEndpoint(error.config?.url) &&
      tokenStorage.getToken()
    ) {
      // A token we sent was refused: drop it and let the AuthContext route the user to sign in.
      tokenStorage.clear();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  },
);
