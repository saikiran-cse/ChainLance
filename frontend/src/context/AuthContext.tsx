import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { UNAUTHORIZED_EVENT } from '../services/api';
import { authService } from '../services/auth.service';
import { setServiceIdentity } from '../services/identity';
import { tokenStorage } from '../services/tokenStorage';
import type { LoginPayload, RegisterPayload, UpdateProfilePayload, User } from '../types/api';
import { AppError, toAppError } from '../utils/errors';

/**
 * loading        restoring a saved session (calling GET /api/users/me)
 * authenticated  the backend accepted the token
 * anonymous      no session
 * error          a token exists but the API could not be reached, so we do not know yet
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error';

export interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  /** Set when a session ended because the token expired or was refused; cleared on the next sign-in. */
  sessionExpired: boolean;
  /** Why the session could not be restored when status is "error". */
  bootError: AppError | null;
  login(payload: LoginPayload): Promise<User>;
  register(payload: RegisterPayload): Promise<User>;
  updateProfile(payload: UpdateProfilePayload): Promise<User>;
  logout(): void;
  retry(): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>(() => (tokenStorage.getToken() ? 'loading' : 'anonymous'));
  const [user, setUser] = useState<User | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [bootError, setBootError] = useState<AppError | null>(null);
  const [attempt, setAttempt] = useState(0);

  const endSession = useCallback((expired: boolean) => {
    tokenStorage.clear();
    setUser(null);
    setBootError(null);
    setStatus('anonymous');
    setSessionExpired(expired);
  }, []);

  // Restore a saved session.
  useEffect(() => {
    if (!tokenStorage.getToken()) {
      setStatus('anonymous');
      return;
    }
    let cancelled = false;
    setStatus('loading');
    authService
      .me()
      .then((me) => {
        if (cancelled) return;
        setUser(me);
        setBootError(null);
        setStatus('authenticated');
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const err = toAppError(e);
        if (err.kind === 'unauthorized') {
          endSession(true);
        } else {
          // Keep the token: the API may just be down. The user can retry without signing in again.
          setBootError(err);
          setStatus('error');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [attempt, endSession]);

  // The Axios interceptor fires this when a token we sent was refused.
  useEffect(() => {
    const onUnauthorized = () => endSession(true);
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [endSession]);

  // Sign out other tabs, and sign out when the token's own expiry time passes.
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === 'chainlance.session' && event.newValue === null) endSession(false);
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [endSession]);

  useEffect(() => {
    if (status !== 'authenticated') return;
    const expiresAt = tokenStorage.expiresAt();
    if (expiresAt == null) return;
    const wait = Math.min(Math.max(expiresAt - Date.now(), 0), 2_000_000_000);
    const timer = window.setTimeout(() => endSession(true), wait);
    return () => window.clearTimeout(timer);
  }, [status, user, endSession]);

  // Services that run outside React (the sample-data layer) need to know who is signed in.
  useEffect(() => {
    setServiceIdentity(user ? { id: user.id, displayName: user.displayName, role: user.role } : null);
  }, [user]);

  const establish = useCallback((token: string, expiresInSeconds: number, nextUser: User) => {
    tokenStorage.save(token, expiresInSeconds);
    setUser(nextUser);
    setBootError(null);
    setSessionExpired(false);
    setStatus('authenticated');
    return nextUser;
  }, []);

  const login = useCallback(
    async (payload: LoginPayload) => {
      const res = await authService.login(payload);
      return establish(res.token, res.expiresInSeconds, res.user);
    },
    [establish],
  );

  const register = useCallback(
    async (payload: RegisterPayload) => {
      const res = await authService.register(payload);
      return establish(res.token, res.expiresInSeconds, res.user);
    },
    [establish],
  );

  const updateProfile = useCallback(async (payload: UpdateProfilePayload) => {
    const updated = await authService.updateProfile(payload);
    setUser(updated);
    return updated;
  }, []);

  const logout = useCallback(() => endSession(false), [endSession]);
  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, sessionExpired, bootError, login, register, updateProfile, logout, retry }),
    [status, user, sessionExpired, bootError, login, register, updateProfile, logout, retry],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
