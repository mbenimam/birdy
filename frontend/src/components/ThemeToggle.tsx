import { Moon, Sun } from 'lucide-react';
import { useTheme } from '../theme/ThemeContext';

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button type="button" className="header__icon-button" aria-pressed={isDark} onClick={toggleTheme}>
      {isDark ? <Moon aria-hidden="true" /> : <Sun aria-hidden="true" />}
      <span className="sr-only">Dark mode</span>
    </button>
  );
}
