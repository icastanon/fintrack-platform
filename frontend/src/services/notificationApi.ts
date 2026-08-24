import type {
  Notification,
  NotificationPageResponse,
  NotificationUnreadCount,
} from '../types/notification'
import { apiRequest } from './apiClient'

export const notificationApi = {
  getNotifications(unreadOnly = false, page = 0, size = 10) {
    const parameters = new URLSearchParams({
      unreadOnly: String(unreadOnly),
      page: String(page),
      size: String(size),
    })

    return apiRequest<NotificationPageResponse>(
      `/api/v1/notifications?${parameters.toString()}`,
    )
  },

  getUnreadCount() {
    return apiRequest<NotificationUnreadCount>(
      '/api/v1/notifications/unread-count',
    )
  },

  markRead(notificationId: number) {
    return apiRequest<Notification>(
      `/api/v1/notifications/${notificationId}/read`,
      {
        method: 'PATCH',
      },
    )
  },
}