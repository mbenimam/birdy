import { apiRequest } from './client';
import type { ChatMessage, ChatTicket } from './types';

export function createChatTicket(): Promise<ChatTicket> {
  return apiRequest('/api/chat/tickets', { method: 'POST' });
}

export function listChatMessages(room: string, signal?: AbortSignal): Promise<ChatMessage[]> {
  return apiRequest(`/api/chat/rooms/${encodeURIComponent(room)}/messages`, {
    query: { limit: 100 },
    signal,
  });
}

/** WebSocket address of a room on the page's own host; wss when the page is served over https. */
export function chatSocketUrl(room: string, ticket: string): string {
  const { protocol, host } = window.location;
  const scheme = protocol === 'https:' ? 'wss:' : 'ws:';
  return `${scheme}//${host}/ws/chat/${encodeURIComponent(room)}?ticket=${encodeURIComponent(ticket)}`;
}
