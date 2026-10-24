import type { Role } from '../types/api';

/**
 * Who is signed in right now, for services that need it outside React (only the mock layer uses this).
 * The real backend derives identity from the JWT; the browser never tells it who the user is.
 */
export interface ServiceIdentity {
  id: number;
  displayName: string;
  role: Role;
}

let current: ServiceIdentity | null = null;

export function setServiceIdentity(identity: ServiceIdentity | null): void {
  current = identity;
}

export function getServiceIdentity(): ServiceIdentity | null {
  return current;
}
