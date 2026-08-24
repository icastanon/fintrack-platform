import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'

function LoadingScreen() {
  return (
    <div className="session-loading">
      <span className="session-loading__spinner" />
      <p>Loading FinTrack...</p>
    </div>
  )
}

export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return <LoadingScreen />
  }

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    )
  }

  return <Outlet />
}

export function PublicOnlyRoute() {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return <LoadingScreen />
  }

  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />
}