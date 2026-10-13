import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { chatSocketUrl, createChatTicket, listChatMessages } from '../../api/chat';
import type { ChatFrame, ChatMessage } from '../../api/types';

export type ChatStatus = 'connecting' | 'open' | 'reconnecting';

export interface ChatOptions {
  /** Delay before the first reconnection attempt; doubled after each failed one. */
  baseDelayMs?: number;
  maxDelayMs?: number;
}

export interface Chat {
  /** History and live messages of the room, oldest first, without duplicates. */
  messages: ChatMessage[];
  /** Usernames currently connected to the room. */
  users: string[];
  status: ChatStatus;
  historyLoading: boolean;
  historyError: unknown;
  /** Sends a message; false when the socket is not open. */
  send: (content: string) => boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function parseFrame(data: unknown): ChatFrame | null {
  if (typeof data !== 'string') return null;
  let frame: unknown;
  try {
    frame = JSON.parse(data);
  } catch {
    return null;
  }
  if (!isRecord(frame)) return null;
  if (frame.type === 'message' && typeof frame.id === 'string' && isRecord(frame.author)) {
    return frame as unknown as ChatMessage;
  }
  if (frame.type === 'presence' && Array.isArray(frame.users)) {
    return { type: 'presence', room: String(frame.room), users: frame.users.map(String) };
  }
  return null;
}

/**
 * Live connection to a chat room. Loads the history over REST, then for each connection asks for a
 * single-use ticket and opens the WebSocket. A lost connection is retried with exponential backoff
 * (and the history reloaded to fill the gap); everything is torn down on unmount.
 * The room must not change during the life of the component: mount it with `key={room}`.
 */
export function useChat(room: string, { baseDelayMs = 1_000, maxDelayMs = 30_000 }: ChatOptions = {}): Chat {
  const queryClient = useQueryClient();
  const history = useQuery({
    queryKey: ['chat', room, 'messages'],
    queryFn: ({ signal }) => listChatMessages(room, signal),
    staleTime: Infinity,
  });

  const [liveMessages, setLiveMessages] = useState<ChatMessage[]>([]);
  const [users, setUsers] = useState<string[]>([]);
  const [status, setStatus] = useState<ChatStatus>('connecting');
  const socketRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    let disposed = false;
    let failedAttempts = 0;
    let hasBeenOpen = false;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    const scheduleReconnect = () => {
      if (disposed) return;
      setStatus('reconnecting');
      setUsers([]);
      const delay = Math.min(maxDelayMs, baseDelayMs * 2 ** failedAttempts);
      failedAttempts += 1;
      retryTimer = setTimeout(() => void connect(), delay);
    };

    const connect = async () => {
      let ticket: string;
      try {
        ({ ticket } = await createChatTicket());
      } catch {
        scheduleReconnect();
        return;
      }
      if (disposed) return;

      const socket = new WebSocket(chatSocketUrl(room, ticket));
      socketRef.current = socket;

      socket.addEventListener('open', () => {
        if (disposed) return;
        setStatus('open');
        if (hasBeenOpen) void queryClient.invalidateQueries({ queryKey: ['chat', room, 'messages'] });
        hasBeenOpen = true;
      });

      socket.addEventListener('message', (event: MessageEvent<unknown>) => {
        if (disposed) return;
        const frame = parseFrame(event.data);
        if (frame === null) return;
        // A frame proves the ticket was accepted, so the next failure starts the backoff over.
        failedAttempts = 0;
        if (frame.type === 'presence') {
          setUsers(frame.users);
        } else {
          setLiveMessages((messages) =>
            messages.some((message) => message.id === frame.id) ? messages : [...messages, frame],
          );
        }
      });

      socket.addEventListener('close', () => {
        if (socketRef.current === socket) socketRef.current = null;
        scheduleReconnect();
      });
    };

    void connect();

    return () => {
      disposed = true;
      clearTimeout(retryTimer);
      socketRef.current?.close();
      socketRef.current = null;
    };
  }, [room, baseDelayMs, maxDelayMs, queryClient]);

  const messages = useMemo(() => {
    const byId = new Map<string, ChatMessage>();
    for (const message of history.data ?? []) byId.set(message.id, message);
    for (const message of liveMessages) byId.set(message.id, message);
    return [...byId.values()].sort((a, b) => a.sentAt.localeCompare(b.sentAt));
  }, [history.data, liveMessages]);

  const send = useCallback((content: string) => {
    const socket = socketRef.current;
    if (socket?.readyState !== WebSocket.OPEN) return false;
    socket.send(JSON.stringify({ content }));
    return true;
  }, []);

  return {
    messages,
    users,
    status,
    historyLoading: history.isPending,
    historyError: history.error,
    send,
  };
}
