import {
  AlertTriangle,
  Bell,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  LoaderCircle,
  RefreshCw,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useAppShell } from '../components/layout/useAppShell'
import { ApiError } from '../services/apiClient'
import { notificationApi } from '../services/notificationApi'
import type {
  Notification,
  NotificationPageResponse,
} from '../types/notification'

const PAGE_SIZE = 10

const EMPTY_PAGE: NotificationPageResponse = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(Number(value))
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatMonth(value: string) {
  const [year, month] = value.split('-').map(Number)

  return new Intl.DateTimeFormat(undefined, {
    month: 'long',
    year: 'numeric',
  }).format(new Date(year, month - 1, 1))
}

function calculatePercentage(notification: Notification) {
  const budgetAmount = Number(notification.budgetAmount)

  if (budgetAmount <= 0) {
    return 0
  }

  return Math.round(
    (Number(notification.spentAmount) / budgetAmount) * 100,
  )
}

function NotificationsPage() {
  const {
    unreadNotificationCount,
    refreshUnreadNotificationCount,
  } = useAppShell()

  const [notificationPage, setNotificationPage] =
    useState<NotificationPageResponse>(EMPTY_PAGE)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [currentPage, setCurrentPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [markingIds, setMarkingIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    let active = true

    notificationApi
      .getNotifications(unreadOnly, currentPage, PAGE_SIZE)
      .then((pageResponse) => {
        if (!active) {
          return
        }

        setNotificationPage(pageResponse)
        setLoadError(null)
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(
            getErrorMessage(
              error,
              'Notifications could not be loaded.',
            ),
          )
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [currentPage, reloadKey, unreadOnly])

  const warningCount = useMemo(
    () =>
      notificationPage.content.filter(
        (notification) =>
          notification.notificationType === 'WARNING',
      ).length,
    [notificationPage.content],
  )

  const exceededCount = useMemo(
    () =>
      notificationPage.content.filter(
        (notification) =>
          notification.notificationType === 'EXCEEDED',
      ).length,
    [notificationPage.content],
  )

  function selectFilter(nextUnreadOnly: boolean) {
    setUnreadOnly(nextUnreadOnly)
    setCurrentPage(0)
    setLoading(true)
    setLoadError(null)
    setActionError(null)
  }

  function refreshNotifications() {
    setLoading(true)
    setLoadError(null)
    setActionError(null)
    setReloadKey((currentKey) => currentKey + 1)
    void refreshUnreadNotificationCount()
  }

  async function markAsRead(notificationId: number) {
    setActionError(null)

    setMarkingIds((currentIds) => {
      const nextIds = new Set(currentIds)
      nextIds.add(notificationId)

      return nextIds
    })

    try {
      const updatedNotification =
        await notificationApi.markRead(notificationId)

      await refreshUnreadNotificationCount()

      if (unreadOnly) {
        const refreshedPage =
          await notificationApi.getNotifications(
            true,
            currentPage,
            PAGE_SIZE,
          )

        if (
          refreshedPage.content.length === 0 &&
          currentPage > 0
        ) {
          setCurrentPage((page) => page - 1)
        } else {
          setNotificationPage(refreshedPage)
        }
      } else {
        setNotificationPage((currentNotificationPage) => ({
          ...currentNotificationPage,
          content: currentNotificationPage.content.map(
            (notification) =>
              notification.id === updatedNotification.id
                ? updatedNotification
                : notification,
          ),
        }))
      }
    } catch (error) {
      setActionError(
        getErrorMessage(
          error,
          'The notification could not be marked as read.',
        ),
      )
    } finally {
      setMarkingIds((currentIds) => {
        const nextIds = new Set(currentIds)
        nextIds.delete(notificationId)

        return nextIds
      })
    }
  }

  return (
    <div className="notifications-page">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Budget alerts</p>
          <h1>Notifications</h1>
          <p className="page-heading__description">
            Review warnings generated as your spending approaches or
            exceeds a monthly budget.
          </p>
        </div>

        <div className="page-actions">
          <button
            className="button button--secondary"
            type="button"
            onClick={refreshNotifications}
            disabled={loading}
          >
            <RefreshCw
              className={loading ? 'notification-spinner' : undefined}
              size={17}
            />
            Refresh
          </button>
        </div>
      </header>

      <section className="notification-summary-grid">
        <article className="notification-summary-card">
          <span className="notification-summary-card__icon">
            <Bell size={21} />
          </span>

          <div>
            <strong>{unreadNotificationCount}</strong>
            <span>Unread notifications</span>
          </div>
        </article>

        <article className="notification-summary-card">
          <span className="notification-summary-card__icon notification-summary-card__icon--warning">
            <AlertTriangle size={21} />
          </span>

          <div>
            <strong>{warningCount}</strong>
            <span>Warnings on this page</span>
          </div>
        </article>

        <article className="notification-summary-card">
          <span className="notification-summary-card__icon notification-summary-card__icon--exceeded">
            <CircleAlert size={21} />
          </span>

          <div>
            <strong>{exceededCount}</strong>
            <span>Exceeded on this page</span>
          </div>
        </article>

        <article className="notification-summary-card">
          <span className="notification-summary-card__icon notification-summary-card__icon--total">
            <CheckCircle2 size={21} />
          </span>

          <div>
            <strong>{notificationPage.totalElements}</strong>
            <span>
              {unreadOnly ? 'Matching unread alerts' : 'Total alerts'}
            </span>
          </div>
        </article>
      </section>

      <section className="content-card notifications-card">
        <div className="notifications-toolbar">
          <div>
            <h2>Notification history</h2>
            <p>Newest notifications appear first.</p>
          </div>

          <div
            className="notification-filter"
            role="group"
            aria-label="Notification status filter"
          >
            <button
              className={
                !unreadOnly
                  ? 'notification-filter__button notification-filter__button--active'
                  : 'notification-filter__button'
              }
              type="button"
              onClick={() => selectFilter(false)}
            >
              All
            </button>

            <button
              className={
                unreadOnly
                  ? 'notification-filter__button notification-filter__button--active'
                  : 'notification-filter__button'
              }
              type="button"
              onClick={() => selectFilter(true)}
            >
              Unread

              {unreadNotificationCount > 0 && (
                <span className="notification-filter__count">
                  {unreadNotificationCount}
                </span>
              )}
            </button>
          </div>
        </div>

        {actionError && (
          <div className="notification-inline-error">
            <AlertTriangle size={17} />
            <span>{actionError}</span>
          </div>
        )}

        {loadError && (
          <div className="notification-feedback">
            <CircleAlert size={30} />
            <h3>Notifications could not be loaded</h3>
            <p>{loadError}</p>

            <button
              className="button button--primary"
              type="button"
              onClick={refreshNotifications}
            >
              Try again
            </button>
          </div>
        )}

        {!loadError &&
          loading &&
          notificationPage.content.length === 0 && (
            <div className="notification-feedback">
              <LoaderCircle
                className="notification-spinner"
                size={30}
              />
              <h3>Loading notifications</h3>
              <p>Your latest budget alerts are being retrieved.</p>
            </div>
          )}

        {!loadError &&
          !loading &&
          notificationPage.content.length === 0 && (
            <div className="notification-feedback">
              <span className="notification-feedback__icon">
                <Bell size={27} />
              </span>

              <h3>
                {unreadOnly
                  ? 'No unread notifications'
                  : 'No notifications yet'}
              </h3>

              <p>
                {unreadOnly
                  ? 'You have reviewed every current budget alert.'
                  : 'Budget warnings and exceeded-budget alerts will appear here.'}
              </p>
            </div>
          )}

        {!loadError && notificationPage.content.length > 0 && (
          <div className="notification-list">
            {notificationPage.content.map((notification) => {
              const percentage = calculatePercentage(notification)
              const isMarking = markingIds.has(notification.id)
              const isExceeded =
                notification.notificationType === 'EXCEEDED'

              return (
                <article
                  className={`notification-row ${
                    notification.read
                      ? 'notification-row--read'
                      : 'notification-row--unread'
                  }`}
                  key={notification.id}
                >
                  <span
                    className={`notification-row__icon ${
                      isExceeded
                        ? 'notification-row__icon--exceeded'
                        : 'notification-row__icon--warning'
                    }`}
                  >
                    {isExceeded ? (
                      <CircleAlert size={21} />
                    ) : (
                      <AlertTriangle size={21} />
                    )}
                  </span>

                  <div className="notification-row__content">
                    <div className="notification-row__heading">
                      <div>
                        <span
                          className={`notification-type notification-type--${notification.notificationType.toLowerCase()}`}
                        >
                          {isExceeded
                            ? 'Budget exceeded'
                            : 'Budget warning'}
                        </span>

                        {!notification.read && (
                          <span className="notification-unread-dot">
                            Unread
                          </span>
                        )}
                      </div>

                      <time dateTime={notification.createdAt}>
                        {formatDateTime(notification.createdAt)}
                      </time>
                    </div>

                    <h3>{notification.categoryName}</h3>
                    <p>{notification.message}</p>

                    <div className="notification-budget-progress">
                      <div className="notification-budget-progress__heading">
                        <span>
                          {formatMonth(notification.budgetMonth)}
                        </span>

                        <strong>
                          {formatCurrency(
                            notification.spentAmount,
                            notification.currency,
                          )}{' '}
                          of{' '}
                          {formatCurrency(
                            notification.budgetAmount,
                            notification.currency,
                          )}
                        </strong>
                      </div>

                      <div className="notification-budget-progress__track">
                        <span
                          className={
                            isExceeded
                              ? 'notification-budget-progress__fill notification-budget-progress__fill--exceeded'
                              : 'notification-budget-progress__fill'
                          }
                          style={{
                            width: `${Math.min(percentage, 100)}%`,
                          }}
                        />
                      </div>

                      <small>{percentage}% of budget used</small>
                    </div>

                    <div className="notification-row__metadata">
                      <span>Notification #{notification.id}</span>
                      <span>Budget #{notification.budgetId}</span>
                      <span>
                        Transaction #{notification.transactionId}
                      </span>

                      {notification.readAt && (
                        <span>
                          Read {formatDateTime(notification.readAt)}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="notification-row__action">
                    {notification.read ? (
                      <span className="notification-read-label">
                        <Check size={16} />
                        Read
                      </span>
                    ) : (
                      <button
                        className="button button--secondary"
                        type="button"
                        onClick={() =>
                          void markAsRead(notification.id)
                        }
                        disabled={isMarking}
                      >
                        {isMarking ? (
                          <LoaderCircle
                            className="notification-spinner"
                            size={16}
                          />
                        ) : (
                          <Check size={16} />
                        )}

                        {isMarking ? 'Saving...' : 'Mark read'}
                      </button>
                    )}
                  </div>
                </article>
              )
            })}
          </div>
        )}

        {!loadError && notificationPage.totalElements > 0 && (
          <footer className="pagination">
            <p>
              Page {notificationPage.page + 1} of{' '}
              {Math.max(notificationPage.totalPages, 1)} ·{' '}
              {notificationPage.totalElements} notification
              {notificationPage.totalElements === 1 ? '' : 's'}
            </p>

            <div className="pagination__actions">
              <button
                className="button pagination__button"
                type="button"
                aria-label="Previous notification page"
                disabled={notificationPage.first || loading}
                onClick={() => {
                  setLoading(true)
                  setCurrentPage((page) => page - 1)
                }}
              >
                <ChevronLeft size={17} />
                Previous
              </button>

              <button
                className="button pagination__button"
                type="button"
                aria-label="Next notification page"
                disabled={notificationPage.last || loading}
                onClick={() => {
                  setLoading(true)
                  setCurrentPage((page) => page + 1)
                }}
              >
                Next
                <ChevronRight size={17} />
              </button>
            </div>
          </footer>
        )}
      </section>
    </div>
  )
}

export default NotificationsPage