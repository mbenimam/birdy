import { Home, User, Users, Zap, type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router';
import { useCurrentUser } from '../auth/AuthContext';
import { cx } from '../lib/cx';

interface NavigationProps {
  id: string;
  open: boolean;
  onClose: () => void;
}

interface NavigationEntry {
  label: string;
  to: string;
  icon: LucideIcon;
}

/** Left navigation: icons only on medium screens, a full-screen drawer on small ones. */
export function Navigation({ id, open, onClose }: NavigationProps) {
  const user = useCurrentUser();
  const entries: NavigationEntry[] = [
    { label: 'Timeline', to: '/', icon: Home },
    { label: 'Chat', to: '/chat', icon: Zap },
    { label: 'People', to: '/people', icon: Users },
    { label: 'Profile', to: `/u/${encodeURIComponent(user.username)}`, icon: User },
  ];

  return (
    <>
      {open && (
        <button type="button" className="nav__backdrop" onClick={onClose}>
          <span className="sr-only">Close navigation</span>
        </button>
      )}
      <nav id={id} className={cx('nav', 'nav--left', open && 'nav--open')} aria-label="Main">
        <ul className="nav__elements">
          {entries.map(({ label, to, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                end
                className={({ isActive }) => cx('nav__element', isActive && 'nav__element--active')}
                onClick={onClose}
              >
                <span className="nav__element-icon">
                  <Icon size={24} aria-hidden="true" />
                </span>
                <span className="nav__element-label">{label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </>
  );
}
