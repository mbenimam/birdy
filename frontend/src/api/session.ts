// sessionStorage keeps the token per tab and drops it when the tab closes, but it stays readable by any script on the page (XSS); an HttpOnly cookie would need backend support.
const STORAGE_KEY = 'birdy.token';

type Listener = () => void;

const listeners = new Set<Listener>();

export function getToken(): string | null {
  return window.sessionStorage.getItem(STORAGE_KEY);
}

export function setToken(token: string): void {
  window.sessionStorage.setItem(STORAGE_KEY, token);
  notify();
}

export function clearToken(): void {
  if (getToken() === null) return;
  window.sessionStorage.removeItem(STORAGE_KEY);
  notify();
}

/** Lets the auth provider follow token changes, including the ones made by the API client on a 401. */
export function subscribeToToken(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function notify(): void {
  for (const listener of listeners) listener();
}
