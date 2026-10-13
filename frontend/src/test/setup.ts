import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, vi } from 'vitest';
import { server } from './server';

/** jsdom has no matchMedia; tests choose the system colour scheme through `setSystemTheme`. */
let systemPrefersLight = false;

export function setSystemTheme(theme: 'light' | 'dark'): void {
  systemPrefersLight = theme === 'light';
}

beforeAll(() => {
  // Any request without a handler is a test bug.
  server.listen({ onUnhandledFrame: 'error' });
});

beforeEach(() => {
  systemPrefersLight = false;
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('light') ? systemPrefersLight : !systemPrefersLight,
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  window.sessionStorage.clear();
  window.localStorage.clear();
  delete document.documentElement.dataset.theme;
  vi.unstubAllGlobals();
});

afterAll(() => {
  server.close();
});
