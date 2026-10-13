import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { getToken } from '../api/session';
import { alice, aliceSession, errorBody, makePost, TOKEN } from '../test/fixtures';
import { accountHandlers, renderApp } from '../test/render';
import { server } from '../test/server';

const emptyFeed = http.get('/api/posts', () => HttpResponse.json([]));

describe('log in', () => {
  it('signs in, stores the token and shows the timeline', async () => {
    let credentials: unknown = null;
    server.use(
      http.post('/api/sessions', async ({ request }) => {
        credentials = await request.json();
        return HttpResponse.json(aliceSession, { status: 201 });
      }),
      ...accountHandlers(),
      emptyFeed,
    );
    const { user, router } = renderApp('/login', { signedIn: false });

    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'correct horse');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('Your timeline is empty')).toBeInTheDocument();
    expect(credentials).toEqual({ username: 'alice', password: 'correct horse' });
    expect(getToken()).toBe(TOKEN);
    expect(router.state.location.pathname).toBe('/');
    expect(screen.getByRole('link', { name: /alice.*your profile/i })).toHaveAttribute('href', '/u/alice');
  });

  it('shows the server message for a wrong password and stays on the form', async () => {
    server.use(
      http.post('/api/sessions', () =>
        HttpResponse.json(errorBody('unauthorized', 'Wrong username or password'), { status: 401 }),
      ),
    );
    const { user, router } = renderApp('/login', { signedIn: false });

    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'not my password');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong username or password');
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
    // The username is kept so only the password has to be typed again.
    expect(screen.getByLabelText('Username')).toHaveValue('alice');
    expect(screen.getByLabelText('Password')).toHaveValue('');
  });

  it('does not call the server while required fields are empty', async () => {
    let calls = 0;
    server.use(
      http.post('/api/sessions', () => {
        calls += 1;
        return HttpResponse.json(aliceSession, { status: 201 });
      }),
    );
    const { user } = renderApp('/login', { signedIn: false });

    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(screen.getByLabelText('Username')).toBeInvalid();
    expect(calls).toBe(0);
  });
});

describe('sign up', () => {
  async function fillSignupForm(user: ReturnType<typeof renderApp>['user']) {
    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Email'), 'alice@example.org');
    await user.type(screen.getByLabelText('Password'), 'correct horse');
    await user.type(screen.getByLabelText('Birthday'), '1990-04-12');
    await user.click(screen.getByRole('button', { name: 'Sign up' }));
  }

  it('is reachable from the log-in form', async () => {
    const { user, router } = renderApp('/login', { signedIn: false });

    await user.click(screen.getByRole('link', { name: 'Sign up' }));

    expect(await screen.findByRole('heading', { name: 'Sign up' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login/signup');
  });

  it('creates the account, then signs in with the same credentials', async () => {
    const calls: { path: string; body: unknown }[] = [];
    server.use(
      http.post('/api/users', async ({ request }) => {
        calls.push({ path: '/api/users', body: await request.json() });
        return HttpResponse.json(alice, { status: 201 });
      }),
      http.post('/api/sessions', async ({ request }) => {
        calls.push({ path: '/api/sessions', body: await request.json() });
        return HttpResponse.json(aliceSession, { status: 201 });
      }),
      ...accountHandlers(),
      emptyFeed,
    );
    const { user } = renderApp('/login/signup', { signedIn: false });

    await fillSignupForm(user);

    expect(await screen.findByText('Your timeline is empty')).toBeInTheDocument();
    expect(calls).toEqual([
      {
        path: '/api/users',
        body: { username: 'alice', email: 'alice@example.org', password: 'correct horse', birthday: '1990-04-12' },
      },
      { path: '/api/sessions', body: { username: 'alice', password: 'correct horse' } },
    ]);
    expect(getToken()).toBe(TOKEN);
  });

  it('shows the field errors of the server next to their inputs', async () => {
    server.use(
      http.post('/api/users', () =>
        HttpResponse.json(
          errorBody('validation_failed', 'Invalid request', {
            email: 'must be a well-formed email address',
            birthday: 'must be a past date',
          }),
          { status: 400 },
        ),
      ),
    );
    const { user } = renderApp('/login/signup', { signedIn: false });

    await fillSignupForm(user);

    const email = await screen.findByLabelText('Email');
    await waitFor(() => {
      expect(email).toBeInvalid();
    });
    expect(email).toHaveAccessibleDescription('must be a well-formed email address');
    expect(screen.getByLabelText('Birthday')).toHaveAccessibleDescription('must be a past date');
    expect(screen.getByLabelText('Username')).toBeValid();
    // What was typed is still there, except the password.
    expect(email).toHaveValue('alice@example.org');
    expect(screen.getByLabelText('Username')).toHaveValue('alice');
    expect(getToken()).toBeNull();
  });

  it('shows a conflict as a form-level message', async () => {
    server.use(
      http.post('/api/users', () =>
        HttpResponse.json(errorBody('conflict', 'This username is already taken'), { status: 409 }),
      ),
    );
    const { user } = renderApp('/login/signup', { signedIn: false });

    await fillSignupForm(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('This username is already taken');
  });
});

describe('protected routes', () => {
  it('redirects an anonymous visitor to /login and back to the requested page after signing in', async () => {
    server.use(
      http.post('/api/sessions', () => HttpResponse.json(aliceSession, { status: 201 })),
      ...accountHandlers(),
      http.get('/api/users', () => HttpResponse.json([])),
    );
    const { user, router } = renderApp('/people?q=bo', { signedIn: false });

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('heading', { name: 'People' })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'correct horse');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('heading', { name: 'People' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/people');
    expect(router.state.location.search).toBe('?q=bo');
  });

  it('returns to /login and forgets the token when the stored session is rejected', async () => {
    server.use(
      http.get('/api/users/me', () => HttpResponse.json(errorBody('unauthorized', 'Session expired'), { status: 401 })),
    );
    const { router } = renderApp('/', { account: false });

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
  });

  it('returns to /login when a later request answers 401', async () => {
    server.use(
      http.get('/api/posts', () => HttpResponse.json(errorBody('unauthorized', 'Session expired'), { status: 401 })),
    );
    const { router } = renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
  });

  it('sends a signed-in user away from /login', async () => {
    server.use(emptyFeed);
    const { router } = renderApp('/login');

    expect(await screen.findByText('Your timeline is empty')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it('logs out: tells the server, drops the token and shows the log-in form', async () => {
    let deleted = false;
    server.use(
      emptyFeed,
      http.delete('/api/sessions/current', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Log out' }));

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(deleted).toBe(true);
    expect(getToken()).toBeNull();
  });

  it('keeps nothing of the previous account in the cache after logging out', async () => {
    const bob = { ...alice, id: 2, username: 'bob', email: 'bob@example.org' };
    let current = alice;
    server.use(
      http.get('/api/users/me', () => HttpResponse.json(current)),
      http.get('/api/users/:username/following', () => HttpResponse.json([])),
      http.get('/api/posts', () =>
        HttpResponse.json([makePost({ content: `Feed of ${current.username}`, author: current, ownedByMe: true })]),
      ),
      http.delete('/api/sessions/current', () => new HttpResponse(null, { status: 204 })),
      http.post('/api/sessions', () => {
        current = bob;
        return HttpResponse.json({ ...aliceSession, token: 'bob-token', user: bob }, { status: 201 });
      }),
    );
    const { user } = renderApp('/', { account: false });
    expect(await screen.findByText('Feed of alice')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Log out' }));
    await user.type(await screen.findByLabelText('Username'), 'bob');
    await user.type(screen.getByLabelText('Password'), 'correct horse');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('Feed of bob')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /your profile/i })).toHaveAttribute('href', '/u/bob');
    expect(screen.queryByText('Feed of alice')).not.toBeInTheDocument();
    expect(getToken()).toBe('bob-token');
  });

  it('shows the 404 page for an unknown address', async () => {
    renderApp('/no/such/page', { signedIn: false });

    expect(await screen.findByRole('heading', { name: 'This page does not exist.' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the timeline' })).toHaveAttribute('href', '/');
  });
});
