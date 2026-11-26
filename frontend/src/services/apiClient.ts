/**
 * Centralized HTTP client for the FarmCrop backend.
 *
 * - One base URL (VITE_API_BASE_URL). Leave it empty in development: Vite proxies /api to the backend.
 * - Every backend response uses the envelope { success, data, message, code, errors }.
 * - Failures always surface as ApiError; callers never receive fake fallback data.
 */

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  message?: string;
  code?: string;
  errors?: ApiFieldError[];
  timestamp?: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: ApiFieldError[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  /** Field-name → message map for showing errors next to form inputs. */
  fieldErrorMap(): Record<string, string> {
    return Object.fromEntries(this.fieldErrors.map((error) => [error.field, error.message]));
  }
}

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '');
export const API_PREFIX = '/api/v1';
const DEFAULT_TIMEOUT_MS = 20_000;

type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Plain objects are sent as JSON; FormData is sent as multipart. */
  body?: unknown;
  query?: Record<string, QueryValue>;
  /** Attach the in-memory access token (default true). */
  auth?: boolean;
  signal?: AbortSignal;
  timeoutMs?: number;
}

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => Promise<boolean>;

let tokenProvider: TokenProvider = () => null;
let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registered by the auth layer so the client can attach the current access token. */
export function setAccessTokenProvider(provider: TokenProvider) {
  tokenProvider = provider;
}

/**
 * Registered by the auth layer. Called once when a request returns 401; resolve true if the
 * session was refreshed and the request should be retried.
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const fullPath = normalizedPath.startsWith(API_PREFIX) ? normalizedPath : `${API_PREFIX}${normalizedPath}`;
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.append(key, String(value));
  });
  const search = params.toString();
  return `${API_BASE_URL}${fullPath}${search ? `?${search}` : ''}`;
}

async function readEnvelope<T>(response: Response): Promise<ApiEnvelope<T> | null> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) return null;
  try {
    return (await response.json()) as ApiEnvelope<T>;
  } catch {
    return null;
  }
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const { method = 'GET', body, query, auth = true, signal, timeoutMs = DEFAULT_TIMEOUT_MS } = options;
  const headers = new Headers({ Accept: 'application/json' });
  let payload: BodyInit | undefined;

  if (body instanceof FormData) {
    payload = body; // the browser sets the multipart boundary
  } else if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
    payload = JSON.stringify(body);
  }

  const token = auth ? tokenProvider() : null;
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const timeoutController = new AbortController();
  const timer = setTimeout(() => timeoutController.abort(), timeoutMs);
  const onExternalAbort = () => timeoutController.abort();
  signal?.addEventListener('abort', onExternalAbort);

  try {
    return await fetch(buildUrl(path, query), {
      method,
      headers,
      body: payload,
      credentials: 'include',
      signal: timeoutController.signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    if (timeoutController.signal.aborted) {
      throw new ApiError(0, 'TIMEOUT', 'The FarmCrop server took too long to respond. Please try again.');
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'Unable to reach the FarmCrop server. Check your connection and try again.');
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', onExternalAbort);
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options);

  if (response.status === 401 && options.auth !== false && unauthorizedHandler) {
    const refreshed = await unauthorizedHandler();
    if (refreshed) response = await send(path, options);
  }

  const envelope = await readEnvelope<T>(response);

  if (!response.ok || !envelope?.success) {
    throw new ApiError(
      response.status,
      envelope?.code ?? (response.ok ? 'INVALID_RESPONSE' : `HTTP_${response.status}`),
      envelope?.message ?? 'The FarmCrop server returned an unexpected response.',
      envelope?.errors ?? [],
    );
  }

  return envelope.data as T;
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) => apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) => apiRequest<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) => apiRequest<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) => apiRequest<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) => apiRequest<T>(path, { ...options, method: 'DELETE' }),
};
