/**
 * Keeps the JWT in localStorage together with its expiry time.
 * Trade-off: localStorage survives reloads but is readable by any script on this origin, so the app must never render
 * untrusted HTML. The backend has no refresh endpoint, so when the token expires the user signs in again.
 */
const KEY = 'chainlance.session';

interface StoredSession {
  token: string;
  expiresAt: number; // epoch ms
}

function read(): StoredSession | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      typeof (parsed as StoredSession).token === 'string' &&
      typeof (parsed as StoredSession).expiresAt === 'number'
    ) {
      return parsed as StoredSession;
    }
  } catch {
    // storage unavailable or corrupted: treat as signed out
  }
  return null;
}

export const tokenStorage = {
  /** The token, or null when absent or already expired (an expired token is removed). */
  getToken(): string | null {
    const session = read();
    if (!session) return null;
    if (session.expiresAt <= Date.now()) {
      tokenStorage.clear();
      return null;
    }
    return session.token;
  },

  save(token: string, expiresInSeconds: number): void {
    try {
      const session: StoredSession = { token, expiresAt: Date.now() + expiresInSeconds * 1000 };
      window.localStorage.setItem(KEY, JSON.stringify(session));
    } catch {
      // storage unavailable: the session will last until the page is reloaded
    }
  },

  /** Epoch ms when the stored token stops being valid, or null. */
  expiresAt(): number | null {
    return read()?.expiresAt ?? null;
  },

  clear(): void {
    try {
      window.localStorage.removeItem(KEY);
    } catch {
      // ignore
    }
  },
};
