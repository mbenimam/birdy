import { QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest';
import { chatSocketUrl } from '../../api/chat';
import { setToken } from '../../api/session';
import { FakeWebSocket } from '../../test/FakeWebSocket';
import { errorBody, makeChatMessage, TOKEN } from '../../test/fixtures';
import { createTestQueryClient } from '../../test/render';
import { server } from '../../test/server';
import { useChat } from './useChat';

const BASE_DELAY = 20;
const MAX_DELAY = 70;

interface Backend {
  ticketAuthorizations: (string | null)[];
  historyRequests: string[];
  /** Number of upcoming ticket requests that must fail. */
  failTickets: number;
}

function startBackend(): Backend {
  const backend: Backend = { ticketAuthorizations: [], historyRequests: [], failTickets: 0 };
  server.use(
    http.post('/api/chat/tickets', ({ request }) => {
      backend.ticketAuthorizations.push(request.headers.get('Authorization'));
      if (backend.failTickets > 0) {
        backend.failTickets -= 1;
        return HttpResponse.json(errorBody('internal_error', 'No ticket'), { status: 500 });
      }
      return HttpResponse.json(
        { ticket: `ticket-${backend.ticketAuthorizations.length}`, expiresAt: '2026-10-01T12:00:30Z' },
        { status: 201 },
      );
    }),
    http.get('/api/chat/rooms/:room/messages', ({ request }) => {
      const url = new URL(request.url);
      backend.historyRequests.push(`${url.pathname}${url.search}`);
      return HttpResponse.json([
        makeChatMessage({ id: 'm1', content: 'First', sentAt: '2026-10-01T12:00:00Z' }),
        makeChatMessage({ id: 'm2', content: 'Second', sentAt: '2026-10-01T12:01:00Z' }),
      ]);
    }),
  );
  return backend;
}

function renderChat(room = 'lobby') {
  const queryClient = createTestQueryClient();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return renderHook(() => useChat(room, { baseDelayMs: BASE_DELAY, maxDelayMs: MAX_DELAY }), { wrapper });
}

async function waitForSocket(count: number): Promise<FakeWebSocket> {
  await waitFor(() => {
    expect(FakeWebSocket.instances).toHaveLength(count);
  });
  return FakeWebSocket.last;
}

/** Delays of the reconnection timers among everything scheduled with setTimeout (libraries use it too). */
function reconnectDelays(spy: MockInstance<typeof setTimeout>): number[] {
  const expected = new Set([BASE_DELAY, 2 * BASE_DELAY, MAX_DELAY]);
  return spy.mock.calls.flatMap(([, delay]) => (delay !== undefined && expected.has(delay) ? [delay] : []));
}

function contents(messages: { content: string }[]): string[] {
  return messages.map((message) => message.content);
}

beforeEach(() => {
  FakeWebSocket.reset();
  vi.stubGlobal('WebSocket', FakeWebSocket);
  setToken(TOKEN);
});

describe('useChat', () => {
  it('asks for a ticket with the session token, then opens the socket of the room with it', async () => {
    const backend = startBackend();
    const { result } = renderChat();
    expect(result.current.status).toBe('connecting');

    const socket = await waitForSocket(1);

    expect(backend.ticketAuthorizations).toEqual([`Bearer ${TOKEN}`]);
    expect(socket.url).toBe('ws://localhost:3000/ws/chat/lobby?ticket=ticket-1');
    expect(result.current.status).toBe('connecting');

    act(() => {
      socket.accept();
    });
    expect(result.current.status).toBe('open');
  });

  it('shows the history of the REST endpoint, oldest first', async () => {
    const backend = startBackend();
    const { result } = renderChat();
    expect(result.current.historyLoading).toBe(true);

    await waitFor(() => {
      expect(contents(result.current.messages)).toEqual(['First', 'Second']);
    });
    expect(result.current.historyLoading).toBe(false);
    expect(backend.historyRequests).toEqual(['/api/chat/rooms/lobby/messages?limit=100']);
  });

  it('appends live messages after the history and ignores duplicates and malformed frames', async () => {
    startBackend();
    const { result } = renderChat();
    const socket = await waitForSocket(1);
    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2);
    });

    act(() => {
      socket.accept();
      socket.push(makeChatMessage({ id: 'm3', content: 'Third', sentAt: '2026-10-01T12:02:00Z' }));
      // Already in the history.
      socket.push(makeChatMessage({ id: 'm2', content: 'Second', sentAt: '2026-10-01T12:01:00Z' }));
      // Delivered twice.
      socket.push(makeChatMessage({ id: 'm3', content: 'Third', sentAt: '2026-10-01T12:02:00Z' }));
      socket.push('not json');
      socket.push({ type: 'unknown' });
    });

    expect(contents(result.current.messages)).toEqual(['First', 'Second', 'Third']);
  });

  it('tracks who is online from presence frames', async () => {
    startBackend();
    const { result } = renderChat();
    const socket = await waitForSocket(1);
    expect(result.current.users).toEqual([]);

    act(() => {
      socket.accept();
      socket.push({ type: 'presence', room: 'lobby', users: ['alice', 'bob'] });
    });
    expect(result.current.users).toEqual(['alice', 'bob']);

    act(() => {
      socket.push({ type: 'presence', room: 'lobby', users: ['alice'] });
    });
    expect(result.current.users).toEqual(['alice']);
    // Presence frames are not messages: only the history is listed.
    await waitFor(() => {
      expect(result.current.historyLoading).toBe(false);
    });
    expect(contents(result.current.messages)).toEqual(['First', 'Second']);
  });

  it('sends `{ content }` once the socket is open, and refuses before', async () => {
    startBackend();
    const { result } = renderChat();
    const socket = await waitForSocket(1);

    expect(result.current.send('too early')).toBe(false);
    expect(socket.sent).toEqual([]);

    act(() => {
      socket.accept();
    });

    expect(result.current.send('hello')).toBe(true);
    expect(socket.sent).toEqual([JSON.stringify({ content: 'hello' })]);
  });

  it('reconnects with a fresh ticket after the connection drops, and reloads the history', async () => {
    const backend = startBackend();
    const { result } = renderChat();
    const first = await waitForSocket(1);
    act(() => {
      first.accept();
      first.push({ type: 'presence', room: 'lobby', users: ['alice', 'bob'] });
    });
    await waitFor(() => {
      expect(backend.historyRequests).toHaveLength(1);
    });

    act(() => {
      first.drop();
    });

    expect(result.current.status).toBe('reconnecting');
    expect(result.current.users).toEqual([]);
    expect(result.current.send('while offline')).toBe(false);

    const second = await waitForSocket(2);
    expect(second.url).toBe('ws://localhost:3000/ws/chat/lobby?ticket=ticket-2');
    expect(backend.ticketAuthorizations).toHaveLength(2);

    act(() => {
      second.accept();
      second.push(makeChatMessage({ id: 'm4', content: 'After the gap', sentAt: '2026-10-01T12:05:00Z' }));
    });

    expect(result.current.status).toBe('open');
    expect(contents(result.current.messages)).toContain('After the gap');
    await waitFor(() => {
      expect(backend.historyRequests).toHaveLength(2);
    });
  });

  it('backs off exponentially, up to the maximum, while attempts keep failing', async () => {
    const backend = startBackend();
    backend.failTickets = 4;
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
    renderChat();

    await waitForSocket(1);

    // Four failed ticket requests, then the fifth succeeds: the delay doubles, then stays at the maximum.
    expect(backend.ticketAuthorizations).toHaveLength(5);
    expect(reconnectDelays(setTimeoutSpy)).toEqual([BASE_DELAY, 2 * BASE_DELAY, MAX_DELAY, MAX_DELAY]);
  });

  it('starts the backoff over once a connection has delivered a frame', async () => {
    startBackend();
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
    renderChat();

    // Refused twice in a row (e.g. close code 1008): the delay doubles.
    (await waitForSocket(1)).drop(1008);
    (await waitForSocket(2)).drop(1008);
    const third = await waitForSocket(3);
    expect(reconnectDelays(setTimeoutSpy)).toEqual([BASE_DELAY, 2 * BASE_DELAY]);

    act(() => {
      third.accept();
      third.push({ type: 'presence', room: 'lobby', users: ['alice'] });
      third.drop();
    });
    await waitForSocket(4);

    expect(reconnectDelays(setTimeoutSpy)).toEqual([BASE_DELAY, 2 * BASE_DELAY, BASE_DELAY]);
  });

  it('closes the socket on unmount and does not reconnect', async () => {
    const backend = startBackend();
    const { unmount } = renderChat();
    const socket = await waitForSocket(1);
    act(() => {
      socket.accept();
    });

    unmount();

    expect(socket.closedByClient).toBe(true);
    await new Promise((resolve) => setTimeout(resolve, BASE_DELAY * 4));
    expect(FakeWebSocket.instances).toHaveLength(1);
    expect(backend.ticketAuthorizations).toHaveLength(1);
  });

  it('cancels a scheduled reconnection on unmount', async () => {
    const backend = startBackend();
    const { unmount } = renderChat();
    const socket = await waitForSocket(1);
    act(() => {
      socket.drop();
    });

    unmount();

    await new Promise((resolve) => setTimeout(resolve, BASE_DELAY * 4));
    expect(FakeWebSocket.instances).toHaveLength(1);
    expect(backend.ticketAuthorizations).toHaveLength(1);
  });
});

describe('chatSocketUrl', () => {
  it('uses ws on an http page and escapes the ticket', () => {
    expect(chatSocketUrl('lobby', 'a b&c')).toBe('ws://localhost:3000/ws/chat/lobby?ticket=a%20b%26c');
  });

  it('uses wss and the page host on an https page', () => {
    vi.stubGlobal('location', { protocol: 'https:', host: 'birdy.example:8443' });

    expect(chatSocketUrl('lobby', 't1')).toBe('wss://birdy.example:8443/ws/chat/lobby?ticket=t1');
  });
});
