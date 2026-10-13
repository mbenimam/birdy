import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <main className="not-found">
      <p className="not-found__code" aria-hidden="true">
        404
      </p>
      <h1 className="not-found__text">This page does not exist.</h1>
      <Link to="/" className="btn btn--white">
        Back to the timeline
      </Link>
    </main>
  );
}
