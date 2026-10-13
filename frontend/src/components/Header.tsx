import { LogOut, Rocket, Search } from 'lucide-react';
import { Link, useNavigate } from 'react-router';
import { useAuth, useCurrentUser } from '../auth/AuthContext';
import { readText } from '../lib/formData';
import { Avatar } from './Avatar';
import { Field } from './Field';
import { ThemeToggle } from './ThemeToggle';

interface HeaderProps {
  navigationId: string;
  navigationOpen: boolean;
  onToggleNavigation: () => void;
}

export function Header({ navigationId, navigationOpen, onToggleNavigation }: HeaderProps) {
  const { signOut } = useAuth();
  const user = useCurrentUser();
  const navigate = useNavigate();

  const search = (formData: FormData) => {
    const q = readText(formData, 'q').trim();
    void navigate(q === '' ? '/people' : `/people?q=${encodeURIComponent(q)}`);
  };

  return (
    <header className="header">
      <button
        type="button"
        className="header__logo header__logo--menu"
        aria-expanded={navigationOpen}
        aria-controls={navigationId}
        onClick={onToggleNavigation}
      >
        <span className="header__logo-icon">
          <Rocket size={30} aria-hidden="true" />
        </span>
        <span className="sr-only">Navigation menu</span>
      </button>
      <Link to="/" className="header__logo header__logo--home">
        <span className="header__logo-label">Birdy</span>
        <span className="header__logo-icon">
          <Rocket size={30} aria-hidden="true" />
        </span>
        <span className="sr-only">home</span>
      </Link>

      <form className="header__search" role="search" action={search}>
        <div className="header__search-input">
          <Field label="Search people" name="q" type="search" compact autoComplete="off" />
          <button type="submit" className="header__search-submit">
            <Search size={20} aria-hidden="true" />
            <span className="sr-only">Search</span>
          </button>
        </div>
        <p className="header__search-title" aria-hidden="true">
          find friends
        </p>
      </form>

      <div className="header__user">
        <Link to={`/u/${encodeURIComponent(user.username)}`} className="header__profile">
          <Avatar username={user.username} size="small" />
          <span className="header__profile-label">{user.username}</span>
          <span className="sr-only">, your profile</span>
        </Link>
        <div className="header__icons">
          <ThemeToggle />
          <button type="button" className="header__icon-button" onClick={() => void signOut()}>
            <LogOut aria-hidden="true" />
            <span className="sr-only">Log out</span>
          </button>
        </div>
      </div>
    </header>
  );
}
