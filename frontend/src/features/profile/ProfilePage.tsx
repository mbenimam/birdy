import { Cake, CalendarDays, Globe, Mail, Users } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import type { UserProfile } from '../../api/types';
import { useCurrentUser } from '../../auth/AuthContext';
import { Avatar } from '../../components/Avatar';
import { EmptyState, ErrorState, LoadingState } from '../../components/States';
import { formatDate } from '../../lib/time';
import { Composer } from '../feed/Composer';
import { Timeline } from '../feed/Timeline';
import { FollowButton } from '../people/FollowButton';
import { useFollowing, useProfile } from '../people/peopleQueries';

function Wall({ profile, isMe }: { profile: UserProfile; isMe: boolean }) {
  return (
    <div className="card-container">
      <section className="card wall" aria-label="Profile summary">
        <div className="wall__img" />
        <div className="wall__profile">
          <div className="wall__profile-picture">
            <Avatar username={profile.username} size="profile" />
          </div>
          {!isMe && <FollowButton username={profile.username} followedByMe={profile.followedByMe} />}
        </div>
        <h1 className="wall__name">{profile.username}</h1>
        <p className="wall__handle">@{profile.username}</p>
        <dl className="wall__links">
          <div className="wall__links-link">
            <dt>Following</dt>
            <dd className="wall__links-count">{profile.followingCount}</dd>
          </div>
          <div className="wall__links-link">
            <dt>{profile.followerCount === 1 ? 'Follower' : 'Followers'}</dt>
            <dd className="wall__links-count">{profile.followerCount}</dd>
          </div>
        </dl>
      </section>
    </div>
  );
}

function About({ profile, isMe }: { profile: UserProfile; isMe: boolean }) {
  const me = useCurrentUser();

  return (
    <div className="card-container">
      <section className="card" aria-labelledby="profile-about-title">
        <div className="card__title">
          <span className="card__title-icon card__title-icon--blue">
            <Globe size={20} aria-hidden="true" />
          </span>
          <h2 id="profile-about-title">About</h2>
        </div>
        <ul className="bio">
          <li className="bio__element">
            <span className="bio__element-icon bio__element-icon--joined">
              <CalendarDays size={20} aria-hidden="true" />
            </span>
            <span className="bio__element-text">Joined {formatDate(profile.createdAt)}</span>
          </li>
          {isMe && (
            <>
              <li className="bio__element">
                <span className="bio__element-icon bio__element-icon--email">
                  <Mail size={20} aria-hidden="true" />
                </span>
                <span className="bio__element-text">{me.email}</span>
              </li>
              <li className="bio__element">
                <span className="bio__element-icon bio__element-icon--birthday">
                  <Cake size={20} aria-hidden="true" />
                </span>
                <span className="bio__element-text">Born {formatDate(me.birthday)}</span>
              </li>
            </>
          )}
        </ul>
      </section>
    </div>
  );
}

function FollowingCard({ username }: { username: string }) {
  const following = useFollowing(username);
  if (!following.isSuccess || following.data.length === 0) return null;

  return (
    <div className="card-container">
      <section className="card" aria-labelledby="profile-following-title">
        <div className="card__title">
          <span className="card__title-icon card__title-icon--green">
            <Users size={20} aria-hidden="true" />
          </span>
          <h2 id="profile-following-title">Following</h2>
        </div>
        <ul className="pelicule">
          {following.data.map((person) => (
            <li key={person.id}>
              <Link to={`/u/${encodeURIComponent(person.username)}`} className="pelicule__friend">
                <Avatar username={person.username} />
                <span className="pelicule__friend-name">{person.username}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

export function ProfilePage() {
  const { username = '' } = useParams();
  const me = useCurrentUser();
  const profile = useProfile(username);

  if (profile.isPending) return <LoadingState label="Loading profile" />;
  if (profile.isError) {
    if (profile.error instanceof ApiError && profile.error.code === 'not_found') {
      return (
        <EmptyState title={`@${username} does not exist`}>
          <Link to="/people" className="btn-text">
            Browse people
          </Link>
        </EmptyState>
      );
    }
    return (
      <ErrorState
        title="This profile could not be loaded"
        message={errorMessage(profile.error)}
        onRetry={() => void profile.refetch()}
      />
    );
  }

  const isMe = profile.data.id === me.id;

  return (
    <div className="content content--wide">
      <Wall profile={profile.data} isMe={isMe} />
      <div className="content__body">
        <div className="content__body-aside">
          <About profile={profile.data} isMe={isMe} />
          <FollowingCard username={profile.data.username} />
        </div>
        <div className="content__body-main">
          {isMe && <Composer />}
          <Timeline
            selector={{ feed: 'user', author: profile.data.username }}
            label={`Posts by ${profile.data.username}`}
            emptyTitle={isMe ? 'You have not posted yet' : `${profile.data.username} has not posted yet`}
          />
        </div>
      </div>
    </div>
  );
}
