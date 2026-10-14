import { MessagesSquare } from 'lucide-react';
import { Link } from 'react-router';
import { useCurrentUser } from '../../auth/AuthContext';
import { Avatar } from '../../components/Avatar';
import { useChatPresence } from '../chat/chatPresence';
import { useFollowing } from './peopleQueries';

/** Right-hand panel: who is online while the chat is open, the accounts the user follows, a shortcut to the chat. */
export function FollowingPanel() {
  const user = useCurrentUser();
  const following = useFollowing(user.username);
  const people = following.data?.filter((person) => person.followedByMe) ?? [];
  const online = useChatPresence();

  return (
    <aside className="nav nav--right" aria-labelledby="following-panel-title">
      {online !== null && (
        <section className="nav__section" aria-labelledby="online-panel-title">
          <h2 id="online-panel-title" className="nav__desc">
            Online now
          </h2>
          <ul className="nav__elements nav__elements--online" aria-labelledby="online-panel-title">
            {online.map((username) => (
              <li key={username}>
                <Link to={`/u/${encodeURIComponent(username)}`} className="nav__element">
                  <span className="nav__element-icon">
                    <Avatar username={username} />
                    <span className="nav__status nav__status--online" />
                  </span>
                  <span className="nav__element-label nav__element-label--strong">
                    {username}
                    {username === user.username && <span className="nav__element-note">&nbsp;(you)</span>}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
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
