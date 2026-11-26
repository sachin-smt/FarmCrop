import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles/globals.css';
import './i18n';
import { AuthProvider } from './hooks/useAuth';
import { PortalProvider } from './features/portals/PortalContext';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <PortalProvider>
          <App />
        </PortalProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
