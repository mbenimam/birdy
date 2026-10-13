import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/errors';

/** Client errors will not go away by retrying; network and server errors get two more attempts. */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
  return failureCount < 2;
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, staleTime: 15_000 },
    },
  });
}
