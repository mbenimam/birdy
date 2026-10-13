import { useRouteError } from 'react-router';
import { errorMessage } from '../api/errors';

/** Error boundary of the route tree: replaces the page when rendering a route throws. */
export function RouteError() {
  const error = useRouteError();

  return (
    <main className="not-found" role="alert">
      <h1 className="not-found__text">Something went wrong.</h1>
      <p>{errorMessage(error)}</p>
      <button
        type="button"
        className="btn btn--white"
        onClick={() => {
          window.location.assign('/');
        }}
      >
        Reload Birdy
      </button>
    </main>
  );
}
