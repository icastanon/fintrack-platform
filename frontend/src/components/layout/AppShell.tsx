import { useCallback, useEffect, useState } from 'react'
import {
  Bell,
  ChartNoAxesCombined,
  CircleDollarSign,
  FileUp,
  Landmark,
  LayoutDashboard,
  LogOut,
  Menu,
  PiggyBank,
  ReceiptText,
  UserRoundCog,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { notificationApi } from '../../services/notificationApi'
import type { AppShellContext } from './useAppShell'

interface NavigationItem {
  label: string
  path: string
  icon: LucideIcon
}

const navigationItems: NavigationItem[] = [
  { label: 'Overview', path: '/', icon: LayoutDashboard },
  { label: 'Accounts', path: '/accounts', icon: Landmark },
  { label: 'Transactions', path: '/transactions', icon: ReceiptText },
  { label: 'Budgets', path: '/budgets', icon: PiggyBank },
  { label: 'Imports', path: '/imports', icon: FileUp },
  { label: 'Notifications', path: '/notifications', icon: Bell },
  { label: 'Profile', path: '/profile', icon: UserRoundCog },
]

function formatUnreadCount(unreadCount: number) {
  return unreadCount > 99 ? '99+' : String(unreadCount)
}

function AppShell() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const [unreadNotificationCount, setUnreadNotificationCount] =
    useState(0)

  const refreshUnreadNotificationCount = useCallback(async () => {
    try {
      const response = await notificationApi.getUnreadCount()
      setUnreadNotificationCount(response.unreadCount)
    } catch {
      // Keep the last successful count when a background refresh fails.
    }
  }, [])

  useEffect(() => {
    document.body.style.overflow = menuOpen ? 'hidden' : ''

    return () => {
      document.body.style.overflow = ''
    }
  }, [menuOpen])

  useEffect(() => {
    let active = true

    notificationApi
      .getUnreadCount()
      .then((response) => {
        if (active) {
          setUnreadNotificationCount(response.unreadCount)
        }
      })
      .catch(() => {
        // Keep the last successful count when the initial request fails.
      })

    const intervalId = window.setInterval(() => {
      void refreshUnreadNotificationCount()
    }, 30_000)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [refreshUnreadNotificationCount])

  const closeMenu = () => {
    setMenuOpen(false)
  }

  const initials = user?.username.slice(0, 2).toUpperCase() ?? 'FT'

  const outletContext: AppShellContext = {
    unreadNotificationCount,
    refreshUnreadNotificationCount,
  }

  return (
    <div className="app-shell">
      <aside className={`sidebar ${menuOpen ? 'sidebar--open' : ''}`}>
        <div className="sidebar__header">
          <NavLink
            className="brand"
            to="/"
            aria-label="FinTrack overview"
            onClick={closeMenu}
          >
            <span className="brand__icon">
              <ChartNoAxesCombined size={22} strokeWidth={2.3} />
            </span>
            <span>FinTrack</span>
          </NavLink>

          <button
            className="icon-button sidebar__close"
            type="button"
            aria-label="Close navigation"
            onClick={closeMenu}
          >
            <X size={22} />
          </button>
        </div>

        <nav className="sidebar__navigation" aria-label="Primary navigation">
          <p className="sidebar__label">Workspace</p>

          {navigationItems.map(({ label, path, icon: Icon }) => (
            <NavLink
              key={path}
              className={({ isActive }) =>
                `navigation-link ${
                  isActive ? 'navigation-link--active' : ''
                }`
              }
              end={path === '/'}
              to={path}
              onClick={closeMenu}
            >
              <Icon size={20} strokeWidth={1.9} />
              <span>{label}</span>

              {path === '/notifications' &&
                unreadNotificationCount > 0 && (
                  <span
                    className="notification-navigation-badge"
                    aria-label={`${unreadNotificationCount} unread notifications`}
                  >
                    {formatUnreadCount(unreadNotificationCount)}
                  </span>
                )}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__footer">
          <div className="user-summary">
            <span className="user-summary__avatar">{initials}</span>

            <span className="user-summary__details">
              <strong>{user?.username}</strong>
              <small>{user?.currency} personal account</small>
            </span>

            <button
              className="icon-button sidebar-logout"
              type="button"
              aria-label="Sign out"
              title="Sign out"
              onClick={() => void logout()}
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>

      {menuOpen && (
        <button
          className="navigation-overlay"
          type="button"
          aria-label="Close navigation"
          onClick={closeMenu}
        />
      )}

      <div className="app-body">
        <header className="mobile-header">
          <button
            className="icon-button"
            type="button"
            aria-label="Open navigation"
            onClick={() => setMenuOpen(true)}
          >
            <Menu size={23} />
          </button>

          <NavLink className="mobile-brand" to="/">
            <CircleDollarSign size={23} />
            <span>FinTrack</span>
          </NavLink>

          <NavLink
            className="icon-button mobile-notification-link"
            to="/notifications"
            aria-label={
              unreadNotificationCount > 0
                ? `Notifications, ${unreadNotificationCount} unread`
                : 'Notifications'
            }
          >
            <Bell size={21} />

            {unreadNotificationCount > 0 && (
              <span className="mobile-notification-badge">
                {formatUnreadCount(unreadNotificationCount)}
              </span>
            )}
          </NavLink>
        </header>

        <main className="page-content">
          <Outlet context={outletContext} />
        </main>
      </div>
    </div>
  )
}

export default AppShell