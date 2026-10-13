import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { expect, it, vi } from 'vitest';
import { RouteError } from './RouteError';

function Broken(): never {
  throw new Error('The page exploded');
}

it('replaces a route that throws while rendering with an error screen', () => {
  // React reports the caught error on the console; keep the test output clean.
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
  const router = createMemoryRouter([{ ErrorBoundary: RouteError, children: [{ path: '/', Component: Broken }] }]);

  render(<RouterProvider router={router} />);

  expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong.');
  expect(screen.getByRole('alert')).toHaveTextContent('The page exploded');
  expect(screen.getByRole('button', { name: 'Reload Birdy' })).toBeInTheDocument();
  expect(consoleError).toHaveBeenCalled();
});
