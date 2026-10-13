import { useDeferredValue } from 'react';
import { Link, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { Avatar } from '../../components/Avatar';
import { Field } from '../../components/Field';
import { EmptyState, ErrorState, LoadingState } from '../../components/States';
import { FollowButton } from './FollowButton';
import { useUserSearch } from './peopleQueries';

/** Directory of the other accounts, filtered by the `q` search parameter. */
export function PeoplePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') ?? '';
  const deferredQuery = useDeferredValue(q.trim());
  const users = useUserSearch(deferredQuery);

  return (
    <div className="users">
      <h1 className="sr-only">People</h1>
      <search className="users-search">
        <Field
          label="Search people by username"
          name="q"
          type="search"
          autoComplete="off"
          value={q}
          onChange={(event) => {
            const next = event.target.value;
            setSearchParams(next === '' ? {} : { q: next }, { replace: true });
          }}
        />
      </search>

      {users.isPending && <LoadingState label="Loading people" />}
      {users.isError && (
        <ErrorState
          title="People could not be loaded"
          message={errorMessage(users.error)}
          onRetry={() => void users.refetch()}
        />
      )}
      {users.isSuccess && users.data.length === 0 && (
        <EmptyState title={deferredQuery === '' ? 'Nobody else is here yet' : `Nobody matches "${deferredQuery}"`} />
      )}
      {users.isSuccess && users.data.length > 0 && (
        <ul className="users-grid" aria-label="People">
          {users.data.map((user) => (
            <li key={user.id} className="user-card">
              <Avatar username={user.username} size="large" />
              <div className="user-card__info">
                <Link to={`/u/${encodeURIComponent(user.username)}`} className="user-card__name">
                  {user.username}
                </Link>
                <span>@{user.username}</span>
              </div>
              <FollowButton username={user.username} followedByMe={user.followedByMe} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
