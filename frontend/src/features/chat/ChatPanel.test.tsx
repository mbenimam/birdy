import { act, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FakeWebSocket } from '../../test/FakeWebSocket';
import { makeChatMessage } from '../../test/fixtures';
import { renderApp } from '../../test/render';
import { server } from '../../test/server';
import { CHAT_COLOR_STORAGE_KEY } from './ChatPanel';

beforeEach(() => {
  FakeWebSocket.reset();
  vi.stubGlobal('WebSocket', FakeWebSocket);
  server.use(
    http.post('/api/chat/tickets', () =>
      HttpResponse.json({ ticket: 't1', expiresAt: '2026-10-01T12:00:30Z' }, { status: 201 }),
    ),
    http.get('/api/chat/rooms/lobby/messages', () =>
      HttpResponse.json([makeChatMessage({ id: 'm1', content: 'Welcome to the lobby' })]),
    ),
  );
});

async function openChat() {
  const view = renderApp('/chat');
  await waitFor(() => {
    expect(FakeWebSocket.instances).toHaveLength(1);
  });
  const socket = FakeWebSocket.last;
  await screen.findByText('Welcome to the lobby');
  return { ...view, socket };
}

describe('chat panel', () => {
  it('connects to the lobby and announces messages in a polite live region', async () => {
    const { socket } = await openChat();
    expect(socket.url).toBe('ws://localhost:3000/ws/chat/lobby?ticket=t1');

    const log = screen.getByRole('log', { name: 'Messages' });
    expect(log).toHaveAttribute('aria-live', 'polite');
    expect(within(log).getByText('Welcome to the lobby')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Connecting');

    act(() => {
      socket.accept();
      socket.push(makeChatMessage({ id: 'm2', content: 'A live one', sentAt: '2026-10-01T12:03:00Z' }));
    });

    expect(screen.getByRole('status')).toHaveTextContent('Connected');
    expect(within(log).getByText('A live one')).toBeInTheDocument();
  });

  it('lists who is online', async () => {
    const { socket } = await openChat();

    act(() => {
      socket.accept();
      socket.push({ type: 'presence', room: 'lobby', users: ['alice', 'bob'] });
    });

    const online = screen.getByRole('list', { name: 'Online now' });
    expect(within(online).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['ALalice', 'BObob']);
  });

  it('sends the message with Enter once connected, and clears the box', async () => {
    const { socket, user } = await openChat();
    const box = screen.getByLabelText('Message');
    const send = screen.getByRole('button', { name: 'Send' });

    await user.type(box, 'hello');
    // Not connected yet: nothing leaves and the draft is kept.
    expect(send).toBeDisabled();
    await user.keyboard('{Enter}');
    expect(socket.sent).toEqual([]);
    expect(box).toHaveValue('hello');

    act(() => {
      socket.accept();
    });
    expect(send).toBeEnabled();
    await user.keyboard('{Enter}');

    expect(socket.sent).toEqual([JSON.stringify({ content: 'hello' })]);
    expect(box).toHaveValue('');

    await user.type(box, 'two{Shift>}{Enter}{/Shift}lines');
    await user.click(send);
    expect(socket.sent[1]).toBe(JSON.stringify({ content: 'two\nlines' }));
  });

  it('marks the messages of the signed-in user', async () => {
    const { socket } = await openChat();

    act(() => {
      socket.accept();
      socket.push(
        makeChatMessage({
          id: 'm2',
          content: 'Mine',
          author: { id: 1, username: 'alice' },
          sentAt: '2026-10-01T12:03:00Z',
        }),
      );
    });

    expect(screen.getByText('Mine').closest('.message')).toHaveClass('message--self');
    expect(screen.getByText('Welcome to the lobby').closest('.message')).toHaveClass('message--other');
  });

  it('remembers the bubble colour picked in the colour picker', async () => {
    const { user } = await openChat();
    const chat = screen.getByRole('region', { name: 'Chat room lobby' });
    expect(chat).toHaveClass('chat--color-7');

    await user.click(screen.getByRole('button', { name: 'Message colour' }));
    const picker = screen.getByRole('dialog', { name: 'Choose the colour of your messages' });
    expect(within(picker).getAllByRole('button', { name: /^Colour/ })).toHaveLength(18);
    expect(within(picker).getByRole('button', { name: 'Colour 7' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(within(picker).getByRole('button', { name: 'Colour 3' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(chat).toHaveClass('chat--color-3');
    expect(window.localStorage.getItem(CHAT_COLOR_STORAGE_KEY)).toBe('3');
  });

  it('traps focus in the colour picker and gives it back on Escape', async () => {
    const { user } = await openChat();
    const opener = screen.getByRole('button', { name: 'Message colour' });

    await user.click(opener);
    const picker = screen.getByRole('dialog', { name: 'Choose the colour of your messages' });
    const close = within(picker).getByRole('button', { name: 'Close' });
    expect(close).toHaveFocus();

    // Shift+Tab from the first control wraps to the last one, Tab from the last wraps to the first.
    await user.tab({ shift: true });
    expect(within(picker).getByRole('button', { name: 'Colour 18' })).toHaveFocus();
    await user.tab();
    expect(close).toHaveFocus();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
    expect(screen.getByRole('region', { name: 'Chat room lobby' })).toHaveClass('chat--color-7');
  });

  it('closes the socket when leaving the page', async () => {
    const { socket, user } = await openChat();
    server.use(http.get('/api/users', () => HttpResponse.json([])));

    await user.click(screen.getByRole('link', { name: 'People' }));

    await screen.findByRole('heading', { name: 'People' });
    expect(socket.closedByClient).toBe(true);
  });
});
