import { MessageCircle, Rocket, Users } from 'lucide-react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';

function readReturnPath(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const { from } = state;
    // Only in-app paths: never follow an absolute or protocol-relative address.
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) return from;
  }
  return '/';
}

/** Split login screen: presentation on the left, the log-in or sign-up form (child route) on the right. */
export function LoginPage() {
  const { status } = useAuth();
  const location = useLocation();

  if (status !== 'anonymous') return <Navigate to={readReturnPath(location.state)} replace />;

  return (
    <div className="login">
      <div className="login__content">
        <section className="login__content-panel login__content-panel--1" aria-label="About Birdy">
          <div className="logobox">
            <Rocket size={36} className="logobox__icon" aria-hidden="true" />
            <span>Birdy</span>
          </div>
          <div className="login__content-panel-card login__content-panel-card--1">
            <div className="presentation">
              <p className="presentation__title">Birdy</p>
              <p className="presentation__description">
                A small social network: post what is on your mind, follow the people you care about and talk with
                everyone in real time.
              </p>
              <ul className="presentation__features">
                <li className="presentation__feature-box">
                  <Rocket size={32} className="presentation__feature-icon" aria-hidden="true" />
                  <h2>Straightforward timeline</h2>
                  <p>Posts, likes and comments from the accounts you follow.</p>
                </li>
                <li className="presentation__feature-box">
                  <MessageCircle size={32} className="presentation__feature-icon" aria-hidden="true" />
                  <h2>Real-time group chat</h2>
                  <p>One lobby, everyone who is online.</p>
                </li>
                <li className="presentation__feature-box">
                  <Users size={32} className="presentation__feature-icon" aria-hidden="true" />
                  <h2>Find people</h2>
                  <p>Search accounts and follow them in one click.</p>
                </li>
              </ul>
            </div>
          </div>
        </section>
        <main className="login__content-panel login__content-panel--2">
          <div className="login__content-panel-card">
            <Outlet />
          </div>
        </main>
      </div>
      <footer className="login__footer">Birdy, a full-stack social network demo.</footer>
    </div>
  );
}
