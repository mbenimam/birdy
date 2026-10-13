import { Navigate, Outlet, useLocation } from 'react-router';
import { errorMessage } from '../api/errors';
import { ErrorState, LoadingState } from '../components/States';
import { useAuth } from './AuthContext';

/** Layout route guarding the application: anonymous visitors are sent to /login and come back afterwards. */
export function RequireAuth() {
  const { status, error, retry } = useAuth();
  const location = useLocation();

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  if (status === 'loading') return <LoadingState label="Loading your account" page />;
  if (status === 'error') {
    return <ErrorState title="Your account could not be loaded" message={errorMessage(error)} onRetry={retry} page />;
  }
  return <Outlet />;
}
