import type { RouteObject } from 'react-router';
import { LoginForm } from './auth/LoginForm';
import { LoginPage } from './auth/LoginPage';
import { RequireAuth } from './auth/RequireAuth';
import { SignupForm } from './auth/SignupForm';
import { AppLayout } from './components/AppLayout';
import { NotFoundPage } from './components/NotFoundPage';
import { RouteError } from './components/RouteError';
import { ChatPage } from './features/chat/ChatPage';
import { HomePage } from './features/feed/HomePage';
import { PeoplePage } from './features/people/PeoplePage';
import { ProfilePage } from './features/profile/ProfilePage';

export const routes: RouteObject[] = [
  {
    ErrorBoundary: RouteError,
    children: [
      {
        path: '/login',
        Component: LoginPage,
        children: [
          { index: true, Component: LoginForm },
          { path: 'signup', Component: SignupForm },
        ],
      },
      {
        Component: RequireAuth,
        children: [
          {
            Component: AppLayout,
            children: [
              { path: '/', Component: HomePage },
              { path: '/u/:username', Component: ProfilePage },
              { path: '/people', Component: PeoplePage },
              { path: '/chat', Component: ChatPage },
            ],
          },
        ],
      },
      { path: '*', Component: NotFoundPage },
    ],
  },
];
