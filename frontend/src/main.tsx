import '@fontsource/lato/400.css';
import '@fontsource/lato/700.css';
import './styles/main.scss';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { App } from './App';
import { createQueryClient } from './lib/queryClient';
import { routes } from './routes';

const container = document.getElementById('root');
if (container === null) throw new Error('The #root element is missing from index.html.');

createRoot(container).render(
  <StrictMode>
    <App queryClient={createQueryClient()} router={createBrowserRouter(routes)} />
  </StrictMode>,
);
