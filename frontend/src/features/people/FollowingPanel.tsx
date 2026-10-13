import { MessagesSquare } from 'lucide-react';
import { Link } from 'react-router';
import { useCurrentUser } from '../../auth/AuthContext';
import { Avatar } from '../../components/Avatar';
import { useFollowing } from './peopleQueries';

/** Right-hand panel: the accounts the user follows and a shortcut to the chat. */
export function FollowingPanel() {
  const user = useCurrentUser();
  const following = useFollowing(user.username);
  const people = following.data?.filter((person) => person.followedByMe) ?? [];

  return (
    <aside className="nav nav--right" aria-labelledby="following-panel-title">
      <h2 id="following-panel-title" className="nav__desc">
        Following
      </h2>
      {following.isSuccess && people.length === 0 && (
        <p className="nav__empty">
          You do not follow anyone yet. <Link to="/people">Find people</Link>.
        </p>
      )}
      {following.isError && <p className="nav__empty">This list could not be loaded.</p>}
      <ul className="nav__elements">
        {people.map((person) => (
          <li key={person.id}>
            <Link to={`/u/${encodeURIComponent(person.username)}`} className="nav__element">
              <span className="nav__element-icon">
                <Avatar username={person.username} />
              </span>
              <span className="nav__element-label nav__element-label--strong">{person.username}</span>
            </Link>
          </li>
        ))}
      </ul>
      <Link to="/chat" className="nav__element nav__button">
        <span className="nav__element-icon">
          <MessagesSquare size={28} aria-hidden="true" />
        </span>
        <span className="nav__element-label nav__element-label--white">Messages</span>
      </Link>
    </aside>
  );
}
