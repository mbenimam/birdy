import { X } from 'lucide-react';
import { useCallback, useId, useState } from 'react';
import { ApiError, errorMessage } from '../../api/errors';
import { useCurrentUser } from '../../auth/AuthContext';
import { Avatar } from '../../components/Avatar';
import { FullScreen } from '../../components/FullScreen';
import { useCreatePost } from './postQueries';

const MAX_POST_LENGTH = 500;
const PROMPT = "What's on your mind?";

function ComposerDialog({ onClose }: { onClose: () => void }) {
  const user = useCurrentUser();
  const textareaId = useId();
  const errorId = useId();
  const [content, setContent] = useState('');
  const creation = useCreatePost();

  const error = creation.error;
  const message =
    error === null ? null : error instanceof ApiError ? (error.fields.content ?? error.message) : errorMessage(error);
  const trimmedLength = content.trim().length;

  const publish = () => {
    if (trimmedLength === 0 || creation.isPending) return;
    creation.mutate(content.trim(), { onSuccess: onClose });
  };

  return (
    <FullScreen label="Create a post" onClose={onClose} className="fullsc--addpost card card--addpost">
      <form className="addpost" action={publish}>
        <div className="addpost__title">
          <h2 className="addpost__heading">Create a post</h2>
          <button type="button" className="btn-circle btn-circle--small" onClick={onClose}>
            <X size={16} aria-hidden="true" />
            <span className="sr-only">Cancel</span>
          </button>
        </div>
        <div className="addpost__user">
          <Avatar username={user.username} />
          <span>{user.username}</span>
        </div>
        <label htmlFor={textareaId} className="sr-only">
          Post content
        </label>
        <textarea
          id={textareaId}
          name="content"
          className="addpost__textarea"
          placeholder={PROMPT}
          required
          maxLength={MAX_POST_LENGTH}
          // The dialog was just opened on purpose, so moving focus into it is expected.
          // eslint-disable-next-line jsx-a11y/no-autofocus
          autoFocus
          value={content}
          aria-invalid={message !== null}
          aria-describedby={message === null ? undefined : errorId}
          onChange={(event) => {
            setContent(event.target.value);
          }}
        />
        <div className="addpost__footer">
          {message !== null ? (
            <p id={errorId} className="inline-error" role="alert">
              {message}
            </p>
          ) : (
            <span />
          )}
          <span>
            {content.length}/{MAX_POST_LENGTH}
          </span>
        </div>
        <button type="submit" className="addpost__publish" disabled={trimmedLength === 0 || creation.isPending}>
          {creation.isPending ? 'Publishing…' : 'Publish'}
        </button>
      </form>
    </FullScreen>
  );
}

/** "Add post" box: a pill that opens the full-screen composer. */
export function Composer() {
  const user = useCurrentUser();
  const [open, setOpen] = useState(false);
  const close = useCallback(() => {
    setOpen(false);
  }, []);

  return (
    <div className="card-container">
      <div className="card">
        <div className="addpost__input">
          <Avatar username={user.username} />
          <button
            type="button"
            className="input__btn"
            onClick={() => {
              setOpen(true);
            }}
          >
            {PROMPT}
          </button>
        </div>
      </div>
      {open && <ComposerDialog onClose={close} />}
    </div>
  );
}
