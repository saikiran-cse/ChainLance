import { beforeEach, describe, expect, it } from 'vitest';
import { tokenStorage } from '../services/tokenStorage';

describe('Token Storage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('saves and retrieves valid token', () => {
    tokenStorage.save('jwt-token-xyz', 3600);
    expect(tokenStorage.getToken()).toBe('jwt-token-xyz');
    expect(tokenStorage.expiresAt()).toBeGreaterThan(Date.now());
  });

  it('returns null and clears expired token', () => {
    tokenStorage.save('expired-jwt', -10); // Expired 10 seconds ago
    expect(tokenStorage.getToken()).toBeNull();
    expect(tokenStorage.expiresAt()).toBeNull();
  });

  it('clears token on logout', () => {
    tokenStorage.save('jwt-token-123', 3600);
    tokenStorage.clear();
    expect(tokenStorage.getToken()).toBeNull();
    expect(tokenStorage.expiresAt()).toBeNull();
  });
});
