import axios from 'axios';
import { env } from '../config/env';
import type { ApiErrorBody } from '../types/api';

export type AppErrorKind =
  | 'network'
  | 'unauthorized'
  | 'forbidden'
  | 'validation'
  | 'conflict'
  | 'not-found'
  | 'pending'
  | 'rate-limited'
  | 'server'
  | 'unknown';

export interface AppErrorInit {
  kind: AppErrorKind;
  status?: number;
  code?: string;
  fieldErrors?: Record<string, string>;
  url?: string;
  method?: string;
}

/** The only error type the UI deals with. Services always throw this, never raw Axios errors. */
export class AppError extends Error {
  readonly kind: AppErrorKind;
  readonly status?: number;
  readonly code?: string;
  readonly fieldErrors: Record<string, string>;
  readonly url?: string;
  readonly method?: string;

  constructor(message: string, init: AppErrorInit) {
    super(message);
    this.name = 'AppError';
    this.kind = init.kind;
    this.status = init.status;
    this.code = init.code;
    this.fieldErrors = init.fieldErrors ?? {};
    this.url = init.url;
    this.method = init.method;
  }
}

function fallbackMessage(status?: number): string {
  switch (status) {
    case 400:
      return 'The request was not valid.';
    case 401:
      return 'You need to sign in to do that.';
    case 403:
      return 'You do not have permission to do that.';
    case 404:
      return 'That could not be found.';
    case 409:
      return 'That conflicts with existing data.';
    case 429:
      return 'Too many requests. Please wait a moment and try again.';
    default:
      return status && status >= 500 ? 'The server had a problem. Please try again.' : 'Something went wrong.';
  }
}

/**
 * Normalise anything thrown by an HTTP call into an AppError.
 *
 * "pending" means the backend has no handler for that URL yet. Spring answers an unmapped URL with a 404 whose code is
 * the generic NOT_FOUND (see GlobalExceptionHandler), while real "missing record" answers carry specific codes such as
 * USER_NOT_FOUND. That difference is how the UI tells "endpoint not built" from "record does not exist".
 */
export function toAppError(error: unknown): AppError {
  if (error instanceof AppError) return error;

  if (axios.isAxiosError(error)) {
    const method = error.config?.method?.toUpperCase();
    const url = error.config?.url;

    if (!error.response) {
      const timedOut = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT';
      const origin = typeof window === 'undefined' ? 'this origin' : window.location.origin;
      return new AppError(
        timedOut
          ? 'The request timed out. Please try again.'
          : `Can't reach the ChainLance API at ${env.apiBaseUrl}. Check that the backend is running and that CORS_ALLOWED_ORIGINS includes ${origin}.`,
        { kind: 'network', url, method },
      );
    }

    const status = error.response.status;
    const data: unknown = error.response.data;
    const body: Partial<ApiErrorBody> = typeof data === 'object' && data !== null ? (data as Partial<ApiErrorBody>) : {};
    const code = typeof body.code === 'string' ? body.code : undefined;
    const fieldErrors: Record<string, string> = {};
    for (const issue of body.fieldErrors ?? []) {
      if (issue && typeof issue.field === 'string' && !(issue.field in fieldErrors)) {
        fieldErrors[issue.field] = issue.message;
      }
    }
    const message = typeof body.message === 'string' && body.message ? body.message : fallbackMessage(status);

    const unmapped =
      (status === 404 && (code === 'NOT_FOUND' || code === undefined)) ||
      (status === 405 && (code === 'METHOD_NOT_ALLOWED' || code === undefined));
    if (unmapped) {
      return new AppError("This part of the ChainLance backend isn't available yet.", {
        kind: 'pending',
        status,
        code,
        url,
        method,
      });
    }

    let kind: AppErrorKind = 'unknown';
    if (status === 401) kind = 'unauthorized';
    else if (status === 403) kind = 'forbidden';
    else if (status === 404) kind = 'not-found';
    else if (status === 409) kind = 'conflict';
    else if (status === 429) kind = 'rate-limited';
    else if (status === 400 || status === 422) kind = 'validation';
    else if (status >= 500) kind = 'server';

    return new AppError(message, { kind, status, code, fieldErrors, url, method });
  }

  return new AppError(error instanceof Error ? error.message : 'Something went wrong.', { kind: 'unknown' });
}

/** Wrap a promise so a rejection always becomes an AppError. */
export async function guard<T>(promise: Promise<T>): Promise<T> {
  try {
    return await promise;
  } catch (e) {
    throw toAppError(e);
  }
}

/** Use in catch blocks where the thrown value is not known to be an AppError yet. */
export function messageOf(error: unknown): string {
  return toAppError(error).message;
}
