import { ChatPanel } from './ChatPanel';

const ROOM = 'lobby';

export function ChatPage() {
  return (
    <div className="chat-page">
      <h1 className="sr-only">Chat</h1>
      <ChatPanel key={ROOM} room={ROOM} />
    </div>
  );
}
