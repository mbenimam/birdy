import { createContext, useContext } from 'react';

/** Usernames online in the open chat room, or null when no chat is on screen. */
export type ChatPresence = string[] | null;

export const ChatPresenceContext = createContext<ChatPresence>(null);
export const SetChatPresenceContext = createContext<(users: ChatPresence) => void>(() => undefined);

/** Who is online in the chat room currently on screen, for the side panel. */
export function useChatPresence(): ChatPresence {
  return useContext(ChatPresenceContext);
}

/** Lets the chat room publish its presence list to the layout. */
export function useSetChatPresence(): (users: ChatPresence) => void {
  return useContext(SetChatPresenceContext);
}
