import { useCallback, useLayoutEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react';
import { ThemeContext, type Theme, type ThemeContextValue } from './ThemeContext';

export const THEME_STORAGE_KEY = 'birdy.theme';
const LIGHT_QUERY = '(prefers-color-scheme: light)';

function readStoredTheme(): Theme | null {
  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  return stored === 'light' || stored === 'dark' ? stored : null;
}

function subscribeToSystemTheme(listener: () => void): () => void {
  const media = window.matchMedia(LIGHT_QUERY);
  media.addEventListener('change', listener);
  return () => {
    media.removeEventListener('change', listener);
  };
}

function getSystemTheme(): Theme {
  return window.matchMedia(LIGHT_QUERY).matches ? 'light' : 'dark';
}

/** Follows the system colour scheme until the user picks a theme; the choice is kept in localStorage. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const systemTheme = useSyncExternalStore(subscribeToSystemTheme, getSystemTheme);
  const [chosenTheme, setChosenTheme] = useState<Theme | null>(readStoredTheme);
  const theme = chosenTheme ?? systemTheme;

  // Layout effect: the attribute must be set before the first paint to avoid a flash of the other theme.
  useLayoutEffect(() => {
    if (chosenTheme === null) {
      delete document.documentElement.dataset.theme;
    } else {
      document.documentElement.dataset.theme = chosenTheme;
    }
  }, [chosenTheme]);

  const toggleTheme = useCallback(() => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    window.localStorage.setItem(THEME_STORAGE_KEY, next);
    setChosenTheme(next);
  }, [theme]);

  const value = useMemo<ThemeContextValue>(() => ({ theme, toggleTheme }), [theme, toggleTheme]);
  return <ThemeContext value={value}>{children}</ThemeContext>;
}
