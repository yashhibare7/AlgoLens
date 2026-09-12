import type {
  ApiErrorBody,
  AuthResponse,
  CreditBalance,
  CreditTransaction,
  DashboardResponse,
  ExecutionDetail,
  ExecutionResponse,
  ExecutionSummary,
  ExplainMode,
  ExplainResponse,
  MetaResponse,
  PageResponse,
  Problem,
  SavedCode,
  SubmissionResponse,
  SubmissionSummary,
  User,
} from '../types';

const TOKEN_KEY = 'algolens.token';

/**
 * Where the API lives.
 *
 * <p>Empty by default, which makes every call same-origin: that covers the dev server (which
 * proxies `/api`), and the single-container deployment where Spring Boot serves this bundle
 * itself. Set `VITE_API_BASE_URL` at build time only when the frontend is hosted separately
 * from the backend -- and remember the backend's `ALGOLENS_CORS_ORIGINS` has to name this
 * origin in return, or the browser will block every request.
 */
const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '');

/** Thrown for every non-2xx response, carrying the backend's structured error body. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors?: Record<string, string>;
  readonly details?: Record<string, unknown>;

  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.error ?? 'UNKNOWN';
    this.fieldErrors = body.fieldErrors;
    this.details = body.details;
  }

  get isUnauthenticated(): boolean {
    return this.status === 401;
  }

  get isOutOfCredits(): boolean {
    return this.status === 402;
  }
}

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      // Private browsing or blocked storage: stay signed out rather than crashing.
      return null;
    }
  },
  set(token: string): void {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* non-fatal: the session just will not survive a reload */
    }
  },
  clear(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* non-fatal */
    }
  },
};

/**
 * Called when any request comes back 401, so the app can drop a token the server no longer
 * accepts (expired, or signed with a rotated secret) instead of retrying with it forever.
 */
let onUnauthenticated: (() => void) | null = null;

export function setUnauthenticatedHandler(handler: () => void): void {
  onUnauthenticated = handler;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = tokenStore.get();
  const headers = new Headers(init.headers);
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json');
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api${path}`, { ...init, headers });
  } catch {
    // A network-level failure is indistinguishable from the backend being down; say so plainly
    // rather than surfacing a raw TypeError.
    throw new ApiError(0, {
      error: 'NETWORK_ERROR',
      message: API_BASE
        ? `Could not reach the AlgoLens backend at ${API_BASE}. It may be asleep — free hosting `
          + 'spins down when idle, so the first request can take up to a minute. Try again.'
        : 'Could not reach the AlgoLens backend. Is it running on port 8080?',
    });
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const body = text ? safeParse(text) : {};

  if (!response.ok) {
    if (response.status === 401 && onUnauthenticated) {
      onUnauthenticated();
    }
    throw new ApiError(response.status, body as Partial<ApiErrorBody>);
  }
  return body as T;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

const get = <T>(path: string) => request<T>(path);
const post = <T>(path: string, body: unknown) =>
  request<T>(path, { method: 'POST', body: JSON.stringify(body) });
const put = <T>(path: string, body: unknown) =>
  request<T>(path, { method: 'PUT', body: JSON.stringify(body) });
const del = (path: string) => request<void>(path, { method: 'DELETE' });

export const api = {
  meta: () => get<MetaResponse>('/meta'),

  auth: {
    register: (name: string, email: string, password: string) =>
      post<AuthResponse>('/auth/register', { name, email, password }),
    login: (email: string, password: string) =>
      post<AuthResponse>('/auth/login', { email, password }),
    me: () => get<User>('/auth/me'),
  },

  executions: {
    run: (payload: {
      language: string;
      code: string;
      problemId?: number;
      savedCodeId?: number;
    }) => post<ExecutionResponse>('/executions', payload),
    list: (page = 0, size = 20) =>
      get<PageResponse<ExecutionSummary>>(`/executions?page=${page}&size=${size}`),
    detail: (id: number) => get<ExecutionDetail>(`/executions/${id}`),
    remove: (id: number) => del(`/executions/${id}`),
  },

  problems: {
    list: (category?: string) =>
      get<Problem[]>(category ? `/problems?category=${encodeURIComponent(category)}` : '/problems'),
    detail: (slugOrId: string, includeSolution = false) =>
      get<Problem>(`/problems/${slugOrId}?includeSolution=${includeSolution}`),
    submit: (slug: string, code: string) =>
      post<SubmissionResponse>(`/problems/${slug}/submit`, { code }),
    submissions: (slug: string) =>
      get<SubmissionSummary[]>(`/problems/${slug}/submissions`),
  },

  savedCode: {
    list: (page = 0, size = 50) =>
      get<PageResponse<SavedCode>>(`/saved-code?page=${page}&size=${size}`),
    detail: (id: number) => get<SavedCode>(`/saved-code/${id}`),
    create: (payload: { title: string; language: string; code: string; problemId?: number }) =>
      post<SavedCode>('/saved-code', payload),
    update: (
      id: number,
      payload: { title: string; language: string; code: string; problemId?: number },
    ) => put<SavedCode>(`/saved-code/${id}`, payload),
    remove: (id: number) => del(`/saved-code/${id}`),
  },

  credits: {
    balance: () => get<CreditBalance>('/credits'),
    transactions: (page = 0, size = 50) =>
      get<PageResponse<CreditTransaction>>(`/credits/transactions?page=${page}&size=${size}`),
  },

  dashboard: () => get<DashboardResponse>('/dashboard'),

  ai: {
    explain: (payload: {
      mode: ExplainMode;
      language: string;
      code: string;
      stepIndex?: number;
      traceExcerpt?: string[];
      question?: string;
    }) => post<ExplainResponse>('/ai/explain', payload),
  },
};
