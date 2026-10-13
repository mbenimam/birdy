import { Heart, MessageSquare, XCircle } from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import type { Post } from '../../api/types';
import { Avatar } from '../../components/Avatar';
import { formatAge, formatDateTime } from '../../lib/time';
import { Comments } from './Comments';
import { useDeletePost, useToggleLike } from './postQueries';

export function PostCard({ post }: { post: Post }) {
  const commentsId = useId();
  const [commentsOpen, setCommentsOpen] = useState(false);
  const like = useToggleLike();
  const removal = useDeletePost();
  const failure = like.error ?? removal.error;
  const profilePath = `/u/${encodeURIComponent(post.author.username)}`;

  return (
    <li className="card-container card-container--post">
      <article className="card card--post" aria-label={`Post by ${post.author.username}`}>
        <div className="post-container">
          <div className="post">
            <div className="post__aside">
              <Avatar username={post.author.username} />
              <div className="post__aside-bar" />
            </div>
            <div className="post__content">
              <div className="post__content-person">
                <Link to={profilePath} className="post__content-person-name">
                  {post.author.username}
                </Link>
                <span className="post__content-person-link" aria-hidden="true">
                  @{post.author.username}
                </span>
                <time
                  className="post__content-person-time"
                  dateTime={post.createdAt}
                  title={formatDateTime(post.createdAt)}
                >
                  {formatAge(post.createdAt)}
                </time>
                {post.ownedByMe && (
                  <button
                    type="button"
                    className="post__content-remove"
                    disabled={removal.isPending}
                    onClick={() => {
                      removal.mutate(post.id);
                    }}
                  >
                    <XCircle aria-hidden="true" />
                    <span className="sr-only">Delete post</span>
                  </button>
                )}
              </div>
              <p className="post__content-body">{post.content}</p>
            </div>
          </div>
        </div>

        {failure !== null && (
          <p className="inline-error" role="alert">
            {errorMessage(failure)}
          </p>
        )}

        <div className="post__footer">
          <button
            type="button"
            className="post__footer-button post__footer-button--heart"
            aria-pressed={post.likedByMe}
            disabled={like.isPending}
            onClick={() => {
              like.mutate(post);
            }}
          >
            <span className="post__footer-button-icon">
              <Heart size={18} aria-hidden="true" />
            </span>
            <span className="sr-only">Like, </span>
            <span>{post.likeCount}</span>
            <span className="sr-only">{post.likeCount === 1 ? ' like' : ' likes'}</span>
          </button>
          <button
            type="button"
            className="post__footer-button post__footer-button--comment"
            aria-expanded={commentsOpen}
            aria-controls={commentsId}
            onClick={() => {
              setCommentsOpen((open) => !open);
            }}
          >
            <span className="post__footer-button-icon">
              <MessageSquare size={18} aria-hidden="true" />
            </span>
            <span className="sr-only">Comments, </span>
            <span>{post.commentCount}</span>
          </button>
        </div>

        <div id={commentsId}>{commentsOpen && <Comments post={post} />}</div>
      </article>
    </li>
  );
}
