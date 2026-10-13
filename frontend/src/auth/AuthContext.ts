import { createContext, use } from 'react';
import type { CurrentUser, Session } from '../api/types';

export type AuthStatus = 'anonymous' | 'loading' | 'authenticated' | 'error';

export interface AuthContextValue {
  status: AuthStatus;
  /** The signed-in account; set when status is `authenticated`. */
  user: CurrentUser | null;
  /** Failure to load the account while a token is present. */
  error: unknown;
  signIn: (session: Session) => void;
  signOut: () => Promise<void>;
  retry: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = use(AuthContext);
  if (value === null) throw new Error('useAuth must be used inside an AuthProvider.');
  return value;
}

/** The signed-in account, for components rendered under the protected layout. */
export function useCurrentUser(): CurrentUser {
  const { user } = useAuth();
  if (user === null) throw new Error('useCurrentUser must be used under the protected layout.');
  return user;
}
