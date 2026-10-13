import { Send, Trash2 } from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import type { Comment, Post } from '../../api/types';
import { Avatar } from '../../components/Avatar';
import { ErrorState, LoadingState } from '../../components/States';
import { formatAge, formatDateTime } from '../../lib/time';
import { useAddComment, useComments, useDeleteComment } from './commentQueries';

const MAX_COMMENT_LENGTH = 300;

interface CommentItemProps {
  comment: Comment;
  /** The server lets a comment be removed by its author and by the author of the post. */
  canDelete: boolean;
  postId: string;
}

function CommentItem({ comment, canDelete, postId }: CommentItemProps) {
  const removal = useDeleteComment(postId);

  return (
    <li className="comment">
      <Avatar username={comment.author.username} size="small" />
      <div className="comment__body">
        <p className="comment__meta">
          <Link to={`/u/${encodeURIComponent(comment.author.username)}`} className="comment__author">
            {comment.author.username}
          </Link>
          <time className="comment__time" dateTime={comment.createdAt} title={formatDateTime(comment.createdAt)}>
            {formatAge(comment.createdAt)}
          </time>
        </p>
        <p>{comment.content}</p>
        {removal.isError && (
          <p className="inline-error" role="alert">
            {errorMessage(removal.error)}
          </p>
        )}
      </div>
      {canDelete && (
        <button
          type="button"
          className="post__content-remove"
          disabled={removal.isPending}
          onClick={() => {
            removal.mutate(comment.id);
          }}
        >
          <Trash2 size={18} aria-hidden="true" />
          <span className="sr-only">Delete comment by {comment.author.username}</span>
        </button>
      )}
    </li>
  );
}

function CommentForm({ postId }: { postId: string }) {
  const inputId = useId();
  const errorId = useId();
  const [content, setContent] = useState('');
  const addition = useAddComment(postId);

  const error = addition.error;
  const message =
    error === null ? null : error instanceof ApiError ? (error.fields.content ?? error.message) : errorMessage(error);

  const submit = () => {
    const trimmed = content.trim();
    if (trimmed === '' || addition.isPending) return;
    addition.mutate(trimmed, {
      onSuccess: () => {
        setContent('');
      },
    });
  };

  return (
    <form action={submit}>
      <div className="post__reply">
        <label htmlFor={inputId} className="sr-only">
          Write a comment
        </label>
        <input
          id={inputId}
          className="post__reply-input"
          name="content"
          placeholder="Write a comment…"
          autoComplete="off"
          required
          maxLength={MAX_COMMENT_LENGTH}
          value={content}
          aria-invalid={message !== null}
          aria-describedby={message === null ? undefined : errorId}
          onChange={(event) => {
            setContent(event.target.value);
          }}
        />
        <button type="submit" className="post__reply-submit" disabled={content.trim() === '' || addition.isPending}>
          <Send size={18} aria-hidden="true" />
          <span className="sr-only">Send comment</span>
        </button>
      </div>
      {message !== null && (
        <p id={errorId} className="inline-error" role="alert">
          {message}
        </p>
      )}
    </form>
  );
}

/** Comment thread of a post, oldest first, with the form to add one. */
export function Comments({ post }: { post: Post }) {
  const comments = useComments(post.id);

  return (
    <section className="comments" aria-label={`Comments on the post by ${post.author.username}`}>
      {comments.isPending && <LoadingState label="Loading comments" />}
      {comments.isError && (
        <ErrorState
          title="The comments could not be loaded"
          message={errorMessage(comments.error)}
          onRetry={() => void comments.refetch()}
        />
      )}
      {comments.isSuccess && comments.data.length === 0 && <p className="comments__empty">No comments yet.</p>}
      {comments.isSuccess && comments.data.length > 0 && (
        <ul className="comments__list">
          {comments.data.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              canDelete={comment.ownedByMe || post.ownedByMe}
              postId={post.id}
            />
          ))}
        </ul>
      )}
      <CommentForm postId={post.id} />
    </section>
  );
}
