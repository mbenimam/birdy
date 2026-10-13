import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { errorBody, TOKEN } from '../test/fixtures';
import { server } from '../test/server';
import { apiRequest } from './client';
import { ApiError } from './errors';
import { getToken, setToken, subscribeToToken } from './session';

async function catchApiError(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error;
    throw error;
  }
  throw new Error('The request was expected to fail.');
}

describe('apiRequest', () => {
  it('attaches the stored token as a bearer Authorization header', async () => {
    setToken(TOKEN);
    let authorization: string | null = 'not captured';
    server.use(
      http.get('/api/users/me', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json({ id: 1 });
      }),
    );

    await expect(apiRequest('/api/users/me')).resolves.toEqual({ id: 1 });
    expect(authorization).toBe(`Bearer ${TOKEN}`);
  });

  it('sends no Authorization header when nobody is signed in', async () => {
    let authorization: string | null = 'not captured';
    server.use(
      http.get('/api/health', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json({ status: 'UP' });
      }),
    );

    await apiRequest('/api/health');
    expect(authorization).toBeNull();
  });

  it('serialises the body as JSON and leaves undefined query parameters out', async () => {
    let received: { url: string; contentType: string | null; body: unknown } | null = null;
    server.use(
      http.post('/api/posts', async ({ request }) => {
        received = {
          url: request.url,
          contentType: request.headers.get('Content-Type'),
          body: await request.json(),
        };
        return HttpResponse.json({ id: 'p1' }, { status: 201 });
      }),
    );

    await apiRequest('/api/posts', {
      method: 'POST',
      body: { content: 'hello' },
      query: { feed: 'home', before: undefined, limit: 20 },
    });

    expect(received).toEqual({
      url: 'http://localhost:3000/api/posts?feed=home&limit=20',
      contentType: 'application/json',
      body: { content: 'hello' },
    });
  });

  it('resolves to undefined for a 204 answer', async () => {
    server.use(http.put('/api/posts/p1/like', () => new HttpResponse(null, { status: 204 })));

    await expect(apiRequest('/api/posts/p1/like', { method: 'PUT' })).resolves.toBeUndefined();
  });

  it('maps a validation failure to an ApiError carrying the field messages', async () => {
    server.use(
      http.post('/api/users', () =>
        HttpResponse.json(
          errorBody('validation_failed', 'Invalid request', { email: 'must be a well-formed email address' }),
          { status: 400 },
        ),
      ),
    );

    const error = await catchApiError(apiRequest('/api/users', { method: 'POST', body: {} }));

    expect(error.status).toBe(400);
    expect(error.code).toBe('validation_failed');
    expect(error.message).toBe('Invalid request');
    expect(error.fields).toEqual({ email: 'must be a well-formed email address' });
  });

  it('maps a conflict to an ApiError without fields', async () => {
    server.use(
      http.post('/api/users', () =>
        HttpResponse.json(errorBody('conflict', 'Username already taken'), { status: 409 }),
      ),
    );

    const error = await catchApiError(apiRequest('/api/users', { method: 'POST', body: {} }));

    expect(error).toMatchObject({ status: 409, code: 'conflict', message: 'Username already taken', fields: {} });
  });

  it('derives the code from the status when the body is not the contract JSON', async () => {
    server.use(
      http.get('/api/posts', () => new HttpResponse('<html>Bad gateway</html>', { status: 502 })),
      http.get('/api/posts/missing', () => new HttpResponse('', { status: 404 })),
    );

    const gateway = await catchApiError(apiRequest('/api/posts'));
    expect(gateway).toMatchObject({ status: 502, code: 'internal_error' });
    expect(gateway.message).toContain('502');

    expect(await catchApiError(apiRequest('/api/posts/missing'))).toMatchObject({ status: 404, code: 'not_found' });
  });

  it('reports an unreachable server as a network error', async () => {
    server.use(http.get('/api/posts', () => HttpResponse.error()));

    const error = await catchApiError(apiRequest('/api/posts'));

    expect(error).toMatchObject({ status: 0, code: 'network_error' });
  });

  it('drops the session and notifies subscribers when an authenticated request gets a 401', async () => {
    setToken(TOKEN);
    const listener = vi.fn();
    const unsubscribe = subscribeToToken(listener);
    server.use(
      http.get('/api/posts', () => HttpResponse.json(errorBody('unauthorized', 'Session expired'), { status: 401 })),
    );

    const error = await catchApiError(apiRequest('/api/posts'));
    unsubscribe();

    expect(error).toMatchObject({ status: 401, code: 'unauthorized', message: 'Session expired' });
    expect(getToken()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('keeps the session on other errors', async () => {
    setToken(TOKEN);
    server.use(http.get('/api/posts', () => HttpResponse.json(errorBody('forbidden', 'No'), { status: 403 })));

    await catchApiError(apiRequest('/api/posts'));

    expect(getToken()).toBe(TOKEN);
  });

  it('does not notify anyone for a 401 on an anonymous request (wrong password at login)', async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToToken(listener);
    server.use(
      http.post('/api/sessions', () =>
        HttpResponse.json(errorBody('unauthorized', 'Wrong username or password'), { status: 401 }),
      ),
    );

    const error = await catchApiError(apiRequest('/api/sessions', { method: 'POST', body: {} }));
    unsubscribe();

    expect(error.message).toBe('Wrong username or password');
    expect(listener).not.toHaveBeenCalled();
  });
});
