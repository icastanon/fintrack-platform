import { useOutletContext } from 'react-router-dom'

export interface AppShellContext {
  unreadNotificationCount: number
  refreshUnreadNotificationCount: () => Promise<void>
}

export function useAppShell() {
  return useOutletContext<AppShellContext>()
}