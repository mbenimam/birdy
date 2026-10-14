import { useCallback, useEffect, useId, useState } from 'react';
import { Outlet } from 'react-router';
import { ChatPresenceContext, SetChatPresenceContext, type ChatPresence } from '../features/chat/chatPresence';
import { FollowingPanel } from '../features/people/FollowingPanel';
import { Header } from './Header';
import { Navigation } from './Navigation';

/** Frame of every signed-in page: header, left navigation, routed content, followed-accounts panel (with who is online while the chat is open). */
export function AppLayout() {
  const navigationId = useId();
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [chatPresence, setChatPresence] = useState<ChatPresence>(null);
  const closeNavigation = useCallback(() => {
    setNavigationOpen(false);
  }, []);

  useEffect(() => {
    if (!navigationOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setNavigationOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [navigationOpen]);

  return (
    <div className="app">
      <Header
        navigationId={navigationId}
        navigationOpen={navigationOpen}
        onToggleNavigation={() => {
          setNavigationOpen((open) => !open);
        }}
      />
      <SetChatPresenceContext value={setChatPresence}>
        <ChatPresenceContext value={chatPresence}>
          <div className="app__body">
            <Navigation id={navigationId} open={navigationOpen} onClose={closeNavigation} />
            <main className="app__main">
              <Outlet />
            </main>
            <FollowingPanel />
          </div>
        </ChatPresenceContext>
      </SetChatPresenceContext>
    </div>
  );
}
