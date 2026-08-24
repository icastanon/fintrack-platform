import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import AuthProvider from './auth/AuthProvider'
import App from './App'
import './index.css'
import './styles/auth.css'
import './styles/accounts.css'
import './styles/transactions.css'
import './styles/budgets.css'
import './styles/dashboard.css'
import './styles/imports.css'
import './styles/notifications.css'
import './styles/notificationBadge.css'
import './styles/profile.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)