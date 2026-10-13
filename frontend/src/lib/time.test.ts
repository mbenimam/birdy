import { describe, expect, it } from 'vitest';
import { formatAge } from './time';

describe('formatAge', () => {
  const now = Date.parse('2026-10-10T12:00:00Z');

  it.each([
    ['2026-10-10T11:59:30Z', 'now'],
    ['2026-10-10T11:55:00Z', '5m'],
    ['2026-10-10T09:00:00Z', '3h'],
    ['2026-10-08T11:00:00Z', '2d'],
    // A clock slightly ahead on the server must not show a negative age.
    ['2026-10-10T12:00:05Z', 'now'],
  ])('formats %s as %s', (iso, expected) => {
    expect(formatAge(iso, now)).toBe(expected);
  });

  it('returns an empty string for an unreadable date', () => {
    expect(formatAge('not a date', now)).toBe('');
  });
});
