import { setupServer } from 'msw/node';

/** Network double used by every test: handlers are added per test with `server.use(...)`. */
export const server = setupServer();
