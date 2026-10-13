import { ApiError, toApiError } from './errors';
import { clearToken, getToken } from './session';

type QueryValue = string | number | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  /** Serialised as JSON. */
  body?: unknown;
  /** Query parameters; undefined values are left out. */
  query?: Record<string, QueryValue>;
  signal?: AbortSignal | undefined;
}

function buildUrl(path: string, query: Record<string, QueryValue> = {}): string {
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (value !== undefined) params.set(name, String(value));
  }
  const search = params.toString();
  return search === '' ? path : `${path}?${search}`;
}

/**
 * Calls the Birdy API on a relative path (the page's own origin) and returns the parsed JSON body,
 * or undefined for an empty one. Failures reject with an ApiError. A 401 on an authenticated
 * request means the session is gone: the stored token is dropped, which sends the user to /login.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, signal } = options;
  const token = getToken();

  const headers = new Headers({ Accept: 'application/json' });
  if (token !== null) headers.set('Authorization', `Bearer ${token}`);
  if (body !== undefined) headers.set('Content-Type', 'application/json');

  const init: RequestInit = { method, headers };
  if (body !== undefined) init.body = JSON.stringify(body);
  if (signal !== undefined) init.signal = signal;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), init);
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new ApiError(0, 'network_error', 'The server cannot be reached. Check your connection and try again.');
  }

  if (!response.ok) {
    if (response.status === 401 && token !== null) clearToken();
    throw await toApiError(response);
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text === '' ? undefined : JSON.parse(text)) as T;
}
