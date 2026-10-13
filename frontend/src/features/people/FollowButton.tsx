import { errorMessage } from '../../api/errors';
import { cx } from '../../lib/cx';
import { useToggleFollow } from './peopleQueries';

interface FollowButtonProps {
  username: string;
  followedByMe: boolean;
}

export function FollowButton({ username, followedByMe }: FollowButtonProps) {
  const toggle = useToggleFollow();

  return (
    <div>
      <button
        type="button"
        className={cx('user-add-btn', followedByMe && 'user-add-btn--remove')}
        aria-pressed={followedByMe}
        disabled={toggle.isPending}
        onClick={() => {
          toggle.mutate({ username, followedByMe });
        }}
      >
        {followedByMe ? 'Unfollow ' : 'Follow '}
        <span className="sr-only">{username}</span>
      </button>
      {toggle.isError && (
        <p className="inline-error" role="alert">
          {errorMessage(toggle.error)}
        </p>
      )}
    </div>
  );
}
