import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { server } from '../test/server';
import { setSystemTheme } from '../test/setup';
import { THEME_STORAGE_KEY } from '../theme/ThemeProvider';
import { initialsOf } from './Avatar';

beforeEach(() => {
  server.use(http.get('/api/posts', () => HttpResponse.json([])));
});

describe('theme', () => {
  it('follows a dark system preference without forcing a theme', async () => {
    setSystemTheme('dark');
    renderApp('/');

    expect(await screen.findByRole('button', { name: 'Dark mode' })).toHaveAttribute('aria-pressed', 'true');
    expect(document.documentElement).not.toHaveAttribute('data-theme');
  });

  it('follows a light system preference', async () => {
    setSystemTheme('light');
    renderApp('/');

    expect(await screen.findByRole('button', { name: 'Dark mode' })).toHaveAttribute('aria-pressed', 'false');
    expect(document.documentElement).not.toHaveAttribute('data-theme');
  });

  it('switches on click and remembers the choice', async () => {
    setSystemTheme('light');
    const { user } = renderApp('/');
    const toggle = await screen.findByRole('button', { name: 'Dark mode' });

    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('prefers the stored choice over the system preference', async () => {
    setSystemTheme('dark');
    window.localStorage.setItem(THEME_STORAGE_KEY, 'light');
    renderApp('/');

    expect(await screen.findByRole('button', { name: 'Dark mode' })).toHaveAttribute('aria-pressed', 'false');
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
  });
});

describe('navigation', () => {
  it('links to every section and marks the current one', async () => {
    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    const links = within(nav).getAllByRole('link');
    expect(links.map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['Timeline', '/'],
      ['Chat', '/chat'],
      ['People', '/people'],
      ['Profile', '/u/alice'],
    ]);
    expect(within(nav).getByRole('link', { name: 'Timeline' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: 'People' })).not.toHaveAttribute('aria-current');
  });

  it('opens as a drawer from the menu button and closes with Escape or the backdrop', async () => {
    const { user } = renderApp('/');
    const menu = await screen.findByRole('button', { name: 'Navigation menu' });
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(menu).toHaveAttribute('aria-controls', nav.id);
    expect(nav).not.toHaveClass('nav--open');

    await user.click(menu);
    expect(menu).toHaveAttribute('aria-expanded', 'true');
    expect(nav).toHaveClass('nav--open');

    await user.keyboard('{Escape}');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(nav).not.toHaveClass('nav--open');

    await user.click(menu);
    await user.click(screen.getByRole('button', { name: 'Close navigation' }));
    expect(nav).not.toHaveClass('nav--open');
    expect(screen.queryByRole('button', { name: 'Close navigation' })).not.toBeInTheDocument();
  });
});

describe('avatar initials', () => {
  it('takes two letters of a single word and the initials of several', () => {
    expect(initialsOf('bob')).toBe('BO');
    expect(initialsOf('ada_lovelace')).toBe('AL');
    expect(initialsOf('x')).toBe('X');
    expect(initialsOf('___')).toBe('?');
  });
});
