import { Palette, Send } from 'lucide-react';
import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useCurrentUser } from '../../auth/AuthContext';
import { Avatar } from '../../components/Avatar';
import { cx } from '../../lib/cx';
import { formatDateTime } from '../../lib/time';
import { CHAT_COLOR_COUNT, ColorPicker, DEFAULT_CHAT_COLOR } from './ColorPicker';
import { useSetChatPresence } from './chatPresence';
import { useChat, type ChatStatus } from './useChat';

export const CHAT_COLOR_STORAGE_KEY = 'birdy.chatColor';
const MAX_MESSAGE_LENGTH = 1000;

const STATUS_LABEL: Record<ChatStatus, string> = {
  connecting: 'Connecting…',
  open: 'Connected',
  reconnecting: 'Connection lost, reconnecting…',
};

function readStoredColor(): number {
  const stored = Number(window.localStorage.getItem(CHAT_COLOR_STORAGE_KEY));
  return Number.isInteger(stored) && stored >= 1 && stored <= CHAT_COLOR_COUNT ? stored : DEFAULT_CHAT_COLOR;
}

/**
 * Group chat of one room: the messages and the box to write one. Who is online goes to the side panel;
 * on screens too narrow for that panel it is listed above the messages instead.
 */
export function ChatPanel({ room }: { room: string }) {
  const me = useCurrentUser();
  const chat = useChat(room);
  const textareaId = useId();
  const [draft, setDraft] = useState('');
  const [color, setColor] = useState(readStoredColor);
  const [pickerOpen, setPickerOpen] = useState(false);
  const closePicker = useCallback(() => {
    setPickerOpen(false);
  }, []);
  const messagesRef = useRef<HTMLDivElement>(null);
  const setPresence = useSetChatPresence();

  // The side panel lists who is online; clear it when the room closes.
  useEffect(() => {
    setPresence(chat.status === 'open' ? chat.users : null);
  }, [setPresence, chat.status, chat.users]);
  useEffect(
    () => () => {
      setPresence(null);
    },
    [setPresence],
  );

  // Keep the newest message in view.
  useEffect(() => {
    const list = messagesRef.current;
    if (list !== null) list.scrollTop = list.scrollHeight;
  }, [chat.messages.length]);

  const canSend = chat.status === 'open' && draft.trim() !== '';

  const submit = () => {
    const content = draft.trim();
    if (content === '') return;
    if (chat.send(content)) setDraft('');
  };

  // Enter sends, Shift+Enter breaks the line.
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  };

  const chooseColor = (next: number) => {
    window.localStorage.setItem(CHAT_COLOR_STORAGE_KEY, String(next));
    setColor(next);
    setPickerOpen(false);
  };

  return (
    <section className={cx('chat', `chat--color-${color}`)} aria-label={`Chat room ${room}`}>
      <p className={cx('chat__status', chat.status === 'open' && 'chat__status--open')} role="status">
        {STATUS_LABEL[chat.status]}
      </p>
      <div className="chat__presence">
        <ul className="chat__users" aria-label="Online now">
          {chat.users.map((username) => (
            <li key={username} className="chat__user">
              <span className="chat__user-avatar">
                <Avatar username={username} size="small" />
                <span className="nav__status nav__status--online" />
              </span>
              {username}
            </li>
          ))}
        </ul>
      </div>

      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- a scrollable region must be reachable from the keyboard */}
      <div className="messages" role="log" aria-live="polite" aria-label="Messages" tabIndex={0} ref={messagesRef}>
        {chat.historyLoading && <p className="state">Loading earlier messages…</p>}
        {chat.historyError !== null && (
          <p className="inline-error">Earlier messages could not be loaded: {errorMessage(chat.historyError)}</p>
        )}
        {!chat.historyLoading && chat.historyError === null && chat.messages.length === 0 && (
          <p className="state">No messages yet. Say hello!</p>
        )}
        {chat.messages.map((message, index) => {
          const isSelf = message.author.id === me.id;
          const isFirst = chat.messages[index - 1]?.author.id !== message.author.id;
          const isLast = chat.messages[index + 1]?.author.id !== message.author.id;
          return (
            <div
              key={message.id}
              className={cx(
                'message',
                isSelf ? 'message--self' : 'message--other',
                isFirst && 'message--first',
                isLast && 'message--last',
              )}
            >
              <span className="message__profile">
                <Avatar username={message.author.username} size="small" />
              </span>
              <div className="message__body">
                <span className={isSelf || !isFirst ? 'sr-only' : 'message__author'}>{message.author.username}</span>
                <p className="message__content" title={formatDateTime(message.sentAt)}>
                  {message.content}
                </p>
              </div>
            </div>
          );
        })}
      </div>

      <form className="input" action={submit}>
        <button
          type="button"
          className="chat__tool"
          onClick={() => {
            setPickerOpen(true);
          }}
        >
          <Palette aria-hidden="true" />
          <span className="sr-only">Message colour</span>
        </button>
        <label htmlFor={textareaId} className="sr-only">
          Message
        </label>
        <textarea
          id={textareaId}
          className="chat__textarea"
          placeholder="Aa…"
          rows={1}
          maxLength={MAX_MESSAGE_LENGTH}
          value={draft}
          onChange={(event) => {
            setDraft(event.target.value);
          }}
          onKeyDown={onKeyDown}
        />
        <button type="submit" className="chat__send" disabled={!canSend}>
          <Send size={20} aria-hidden="true" />
          <span className="sr-only">Send</span>
        </button>
      </form>

      {pickerOpen && <ColorPicker selected={color} onSelect={chooseColor} onClose={closePicker} />}
    </section>
  );
}
