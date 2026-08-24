import { Navigate, Route, Routes } from 'react-router-dom'
import AuthLayout from './components/auth/AuthLayout'
import AppShell from './components/layout/AppShell'
import {
  ProtectedRoute,
  PublicOnlyRoute,
} from './components/routing/RouteGuards'
import AccountsPage from './pages/AccountsPage'
import BudgetsPage from './pages/BudgetsPage'
import DashboardPage from './pages/DashboardPage'
import ImportsPage from './pages/ImportsPage'
import LoginPage from './pages/LoginPage'
import NotificationsPage from './pages/NotificationsPage'
import ProfilePage from './pages/ProfilePage'
import RegisterPage from './pages/RegisterPage'
import TransactionsPage from './pages/TransactionsPage'

function App() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route path="budgets" element={<BudgetsPage />} />
          <Route path="imports" element={<ImportsPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App