import { cx } from '../lib/cx';

const HUE_COUNT = 12;

function hueIndex(username: string): number {
  let hash = 0;
  for (const char of username) hash = (hash * 31 + (char.codePointAt(0) ?? 0)) % 9973;
  return hash % HUE_COUNT;
}

/** Up to two initials: "ada_lovelace" gives "AL", "bob" gives "BO". */
export function initialsOf(username: string): string {
  const parts = username.split(/[^A-Za-z0-9]+/).filter(Boolean);
  const [first, second] = parts;
  if (first === undefined) return '?';
  const letters = second === undefined ? first.slice(0, 2) : `${first.charAt(0)}${second.charAt(0)}`;
  return letters.toUpperCase();
}

interface AvatarProps {
  username: string;
  size?: 'small' | 'medium' | 'large' | 'profile';
}

/** Decorative generated avatar; the username is always printed next to it, so it is hidden from assistive technology. */
export function Avatar({ username, size = 'medium' }: AvatarProps) {
  return (
    <span
      className={cx('avatar', size !== 'medium' && `avatar--${size}`, `avatar--hue-${hueIndex(username)}`)}
      aria-hidden="true"
    >
      {initialsOf(username)}
    </span>
  );
}
