/**
 * Types for the endpoints that EXIST in the backend today (verified against the Java controllers and DTOs in
 * backend/src/main/java/com/chainlance/{controller,dto}). Field names match the Java records exactly.
 * Jackson is configured with default-property-inclusion: non_null, so null fields are simply omitted.
 */

export type Role = 'CLIENT' | 'FREELANCER' | 'ADMIN';

/** UserResponse */
export interface User {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  bio?: string | null;
  createdAt: string;
}

/** AuthResponse (register and login) */
export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

/** RegisterRequest: only CLIENT or FREELANCER may self-register. */
export interface RegisterPayload {
  email: string;
  password: string;
  displayName: string;
  role: Exclude<Role, 'ADMIN'>;
}

/** LoginRequest */
export interface LoginPayload {
  email: string;
  password: string;
}

/** UpdateProfileRequest: email, role and password are deliberately not editable. */
export interface UpdateProfilePayload {
  displayName: string;
  bio?: string;
}

/** WalletResponse: a wallet only appears here after the backend verified the signature. */
export interface Wallet {
  id: number;
  address: string;
  verifiedAt: string;
}

/** ChallengeResponse: `message` must be signed exactly as given (personal_sign). */
export interface WalletChallenge {
  nonce: string;
  message: string;
  address: string;
  expiresAt: string;
}

/** ApiError.FieldIssue */
export interface ApiFieldIssue {
  field: string;
  message: string;
}

/** ApiError: the one JSON error format used by every failing backend response. */
export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  fieldErrors?: ApiFieldIssue[];
}
