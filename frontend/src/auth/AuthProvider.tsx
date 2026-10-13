import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import { deleteCurrentSession, getCurrentUser } from '../api/accounts';
import { clearToken, getToken, setToken, subscribeToToken } from '../api/session';
import type { Session } from '../api/types';
import { AuthContext, type AuthContextValue, type AuthStatus } from './AuthContext';

const currentUserKey = ['me'] as const;

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const token = useSyncExternalStore(subscribeToToken, getToken);

  const me = useQuery({
    queryKey: currentUserKey,
    queryFn: ({ signal }) => getCurrentUser(signal),
    enabled: token !== null,
    staleTime: Infinity,
  });

  // Whatever ended the session (sign-out, or a 401 seen by the API client), no cached data of that account may survive it.
  useEffect(() => {
    if (token === null) queryClient.clear();
  }, [token, queryClient]);

  const signIn = useCallback((session: Session) => {
    setToken(session.token);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await deleteCurrentSession();
    } catch {
      // The local session ends even when the server cannot be told.
    } finally {
      clearToken();
    }
  }, []);

  const { data, error, refetch } = me;
  const value = useMemo<AuthContextValue>(() => {
    let status: AuthStatus = 'loading';
    if (token === null) status = 'anonymous';
    else if (data !== undefined) status = 'authenticated';
    else if (error !== null) status = 'error';

    return {
      status,
      user: token !== null && data !== undefined ? data : null,
      error,
      signIn,
      signOut,
      retry: () => void refetch(),
    };
  }, [token, data, error, signIn, signOut, refetch]);

  return <AuthContext value={value}>{children}</AuthContext>;
}
