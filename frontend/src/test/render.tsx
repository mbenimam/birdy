import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { createMemoryRouter } from 'react-router';
import { App } from '../App';
import { setToken } from '../api/session';
import type { UserSummary } from '../api/types';
import { routes } from '../routes';
import { alice, TOKEN } from './fixtures';
import { server } from './server';

export function createTestQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 15_000 } } });
}

/** Handlers every signed-in page needs: the account itself and the "Following" side panel. */
export function accountHandlers(following: UserSummary[] = []) {
  return [
    http.get('/api/users/me', () => HttpResponse.json(alice)),
    http.get('/api/users/alice/following', () => HttpResponse.json(following)),
  ];
}

interface RenderAppOptions {
  /** Start with Alice's token in the session (default) or as an anonymous visitor. */
  signedIn?: boolean;
  /** Register the default account handlers (default); pass false when the test defines its own. */
  account?: boolean;
}

/** Renders the whole application on a memory router at `path`. */
export function renderApp(path = '/', { signedIn = true, account = true }: RenderAppOptions = {}) {
  if (signedIn) {
    setToken(TOKEN);
    if (account) server.use(...accountHandlers());
  }
  const queryClient = createTestQueryClient();
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const view = render(<App queryClient={queryClient} router={router} />);
  return { ...view, router, queryClient, user: userEvent.setup() };
}

/** A promise settled by the test, to hold a response back while asserting on the pending UI. */
export function deferred<T = void>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}
