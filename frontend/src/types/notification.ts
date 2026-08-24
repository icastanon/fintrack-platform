import type { PageResponse } from './account'

export type NotificationType = 'WARNING' | 'EXCEEDED'

export interface Notification {
  id: number
  budgetId: number
  categoryId: number
  categoryName: string
  transactionId: number
  budgetMonth: string
  notificationType: NotificationType
  budgetAmount: number
  spentAmount: number
  currency: string
  message: string
  read: boolean
  readAt: string | null
  createdAt: string
}

export interface NotificationUnreadCount {
  unreadCount: number
}

export type NotificationPageResponse = PageResponse<Notification>