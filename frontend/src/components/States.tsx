import type { ReactNode } from 'react';
import { cx } from '../lib/cx';

interface LoadingStateProps {
  label: string;
  /** Fill the whole viewport (used before the layout exists). */
  page?: boolean;
}

export function LoadingState({ label, page = false }: LoadingStateProps) {
  return (
    <div className={cx('state', page && 'state--page')} role="status">
      <div className="spinner" aria-hidden="true" />
      <p>{label}…</p>
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  children?: ReactNode;
}

export function EmptyState({ title, children }: EmptyStateProps) {
  return (
    <div className="state">
      <p className="state__title">{title}</p>
      {children}
    </div>
  );
}

interface ErrorStateProps {
  title: string;
  message: string;
  onRetry?: () => void;
  page?: boolean;
}

export function ErrorState({ title, message, onRetry, page = false }: ErrorStateProps) {
  return (
    <div className={cx('state', 'state--error', page && 'state--page')} role="alert">
      <p className="state__title">{title}</p>
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn-text" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
