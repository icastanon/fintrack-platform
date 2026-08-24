import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Gauge,
  Pencil,
  PiggyBank,
  Plus,
  RefreshCw,
  Target,
  TriangleAlert,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../services/apiClient'
import { budgetApi } from '../services/budgetApi'
import { categoryApi } from '../services/categoryApi'
import type {
  Budget,
  BudgetPageResponse,
  BudgetStatus,
  MonthlyBudgetUsage,
} from '../types/budget'
import type { Category } from '../types/category'

const PAGE_SIZE = 12

const EMPTY_PAGE: BudgetPageResponse = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

interface BudgetForm {
  categoryId: string
  budgetMonth: string
  amount: string
  warningThresholdPercentage: string
}

type DialogMode = 'create' | 'edit' | null

function getCurrentMonth() {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')

  return `${year}-${month}`
}

function formatMonth(month: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${month}-01T12:00:00Z`))
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(Number(value))
}

function formatPercentage(value: number) {
  return `${Number(value).toFixed(1)}%`
}

function formatStatus(status: BudgetStatus) {
  if (status === 'ON_TRACK') {
    return 'On track'
  }

  if (status === 'WARNING') {
    return 'Warning'
  }

  return 'Exceeded'
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

function BudgetsPage() {
  const { user } = useAuth()
  const defaultCurrency = user?.currency || 'USD'
  const initialMonth = getCurrentMonth()

  const [selectedMonth, setSelectedMonth] = useState(initialMonth)
  const [appliedMonth, setAppliedMonth] = useState(initialMonth)
  const [currentPage, setCurrentPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)

  const [budgetPage, setBudgetPage] = useState<BudgetPageResponse>(EMPTY_PAGE)
  const [budgetUsage, setBudgetUsage] = useState<MonthlyBudgetUsage>({
    month: initialMonth,
    currency: defaultCurrency,
    budgets: [],
  })
  const [categories, setCategories] = useState<Category[]>([])

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [categoryError, setCategoryError] = useState<string | null>(null)
  const [pageMessage, setPageMessage] = useState<string | null>(null)

  const [dialogMode, setDialogMode] = useState<DialogMode>(null)
  const [dialogLoading, setDialogLoading] = useState(false)
  const [selectedBudget, setSelectedBudget] = useState<Budget | null>(null)
  const [form, setForm] = useState<BudgetForm>({
    categoryId: '',
    budgetMonth: initialMonth,
    amount: '',
    warningThresholdPercentage: '80',
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formMessage, setFormMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let active = true

    categoryApi
      .getCategories()
      .then((response) => {
        if (active) {
          setCategories(response)
          setCategoryError(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setCategoryError(
            getErrorMessage(error, 'Categories could not be loaded.'),
          )
        }
      })

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true

    Promise.all([
      budgetApi.getBudgets(appliedMonth, currentPage, PAGE_SIZE),
      budgetApi.getBudgetUsage(appliedMonth),
    ])
      .then(([pageResponse, usageResponse]) => {
        if (!active) {
          return
        }

        setBudgetPage(pageResponse)
        setBudgetUsage(usageResponse)
        setLoadError(null)
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(
            getErrorMessage(error, 'Budgets could not be loaded.'),
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
  }, [appliedMonth, currentPage, reloadKey])

  const usageByBudgetId = useMemo(
    () =>
      new Map(
        budgetUsage.budgets.map((usage) => [usage.budgetId, usage]),
      ),
    [budgetUsage.budgets],
  )

  const availableCategories = useMemo(() => {
    const budgetedCategoryIds = new Set(
      budgetUsage.budgets.map((budget) => budget.categoryId),
    )

    return categories.filter(
      (category) => !budgetedCategoryIds.has(category.id),
    )
  }, [budgetUsage.budgets, categories])

  const totalAllocated = budgetUsage.budgets.reduce(
    (total, budget) => total + Number(budget.budgetAmount),
    0,
  )

  const totalSpent = budgetUsage.budgets.reduce(
    (total, budget) => total + Number(budget.spentAmount),
    0,
  )

  const attentionCount = budgetUsage.budgets.filter(
    (budget) => budget.status !== 'ON_TRACK',
  ).length

  function refreshBudgets() {
    setLoading(true)
    setReloadKey((current) => current + 1)
  }

  function handleMonthSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!selectedMonth) {
      setLoadError('Select a month to view its budgets.')
      return
    }

    setLoadError(null)
    setPageMessage(null)
    setCurrentPage(0)
    setAppliedMonth(selectedMonth)
    setLoading(true)
    setReloadKey((current) => current + 1)
  }

  function changePage(page: number) {
    setCurrentPage(page)
    setLoading(true)
  }

  function openCreateDialog() {
    const firstCategory = availableCategories[0]

    setSelectedBudget(null)
    setForm({
      categoryId: firstCategory ? String(firstCategory.id) : '',
      budgetMonth: appliedMonth,
      amount: '',
      warningThresholdPercentage: '80',
    })
    setFieldErrors({})
    setFormMessage(null)
    setDialogMode('create')
  }

  async function openEditDialog(budgetId: number) {
    setSelectedBudget(null)
    setFieldErrors({})
    setFormMessage(null)
    setDialogMode('edit')
    setDialogLoading(true)

    try {
      const budget = await budgetApi.getBudget(budgetId)

      setSelectedBudget(budget)
      setForm({
        categoryId: String(budget.categoryId),
        budgetMonth: budget.budgetMonth,
        amount: String(budget.amount),
        warningThresholdPercentage: String(
          budget.warningThresholdPercentage,
        ),
      })
    } catch (error) {
      setDialogMode(null)
      setLoadError(
        getErrorMessage(error, 'The budget could not be loaded.'),
      )
    } finally {
      setDialogLoading(false)
    }
  }

  function closeDialog() {
    if (submitting) {
      return
    }

    setDialogMode(null)
    setSelectedBudget(null)
    setFieldErrors({})
    setFormMessage(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFieldErrors({})
    setFormMessage(null)

    try {
      if (dialogMode === 'create') {
        await budgetApi.createBudget({
          categoryId: Number(form.categoryId),
          budgetMonth: form.budgetMonth,
          amount: Number(form.amount),
          warningThresholdPercentage: Number(
            form.warningThresholdPercentage,
          ),
        })

        setPageMessage('Budget created successfully.')
      } else if (selectedBudget) {
        await budgetApi.updateBudget(selectedBudget.id, {
          amount: Number(form.amount),
          warningThresholdPercentage: Number(
            form.warningThresholdPercentage,
          ),
          version: selectedBudget.version,
        })

        setPageMessage('Budget updated successfully.')
      }

      setDialogMode(null)
      setSelectedBudget(null)
      setCurrentPage(0)
      refreshBudgets()
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors)
        setFormMessage(error.message)
      } else {
        setFormMessage('The budget could not be saved.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDeleteBudget() {
    if (!selectedBudget) {
      return
    }

    const confirmed = window.confirm(
      `Delete the ${selectedBudget.categoryName} budget for ${formatMonth(selectedBudget.budgetMonth)}?`,
    )

    if (!confirmed) {
      return
    }

    setSubmitting(true)
    setFormMessage(null)

    try {
      await budgetApi.deleteBudget(
        selectedBudget.id,
        selectedBudget.version,
      )

      setDialogMode(null)
      setSelectedBudget(null)
      setPageMessage('Budget deleted successfully.')
      setCurrentPage(0)
      refreshBudgets()
    } catch (error) {
      setFormMessage(
        getErrorMessage(error, 'The budget could not be deleted.'),
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="budgets-page">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Monthly planning</p>
          <h1>Budgets</h1>
          <p className="page-heading__description">
            Set category limits and compare them with your processed
            spending for the selected month.
          </p>
        </div>

        <div className="page-actions">
          <button
            className="button button--secondary"
            onClick={refreshBudgets}
            type="button"
          >
            <RefreshCw size={17} />
            Refresh
          </button>

          <button
            className="button button--primary"
            disabled={
              availableCategories.length === 0 ||
              Boolean(categoryError)
            }
            onClick={openCreateDialog}
            type="button"
          >
            <Plus size={18} />
            Add budget
          </button>
        </div>
      </header>

      <section className="content-card budget-month-card">
        <form className="budget-month-form" onSubmit={handleMonthSubmit}>
          <label>
            <CalendarDays size={18} />
            <span>Budget month</span>
            <input
              onChange={(event) => setSelectedMonth(event.target.value)}
              required
              type="month"
              value={selectedMonth}
            />
          </label>

          <button className="button button--secondary" type="submit">
            View month
          </button>
        </form>

        <p>
          Showing budget activity for{' '}
          <strong>{formatMonth(appliedMonth)}</strong>.
        </p>
      </section>

      {categoryError && (
        <div className="form-message form-message--error" role="alert">
          {categoryError}
        </div>
      )}

      {loadError && (
        <div className="form-message form-message--error" role="alert">
          {loadError}
        </div>
      )}

      {pageMessage && (
        <div className="form-message form-message--success" role="status">
          {pageMessage}
        </div>
      )}

      <section className="budget-overview" aria-label="Budget overview">
        <article className="budget-overview__item">
          <span className="budget-overview__icon">
            <Target size={20} />
          </span>
          <div>
            <strong>
              {formatCurrency(
                totalAllocated,
                budgetUsage.currency || defaultCurrency,
              )}
            </strong>
            <span>Total allocated</span>
          </div>
        </article>

        <article className="budget-overview__item">
          <span className="budget-overview__icon budget-overview__icon--green">
            <Gauge size={20} />
          </span>
          <div>
            <strong>
              {formatCurrency(
                totalSpent,
                budgetUsage.currency || defaultCurrency,
              )}
            </strong>
            <span>Processed spending</span>
          </div>
        </article>

        <article className="budget-overview__item">
          <span className="budget-overview__icon budget-overview__icon--warning">
            <TriangleAlert size={20} />
          </span>
          <div>
            <strong>{attentionCount}</strong>
            <span>Need attention</span>
          </div>
        </article>
      </section>

      <section className="content-card budgets-card">
        <header className="content-card__header">
          <div>
            <h2>{formatMonth(appliedMonth)} budgets</h2>
            <p>
              Spending totals include processed expense transactions across
              all your accounts.
            </p>
          </div>
        </header>

        {loading ? (
          <div className="budgets-loading">
            <span className="budgets-loading__spinner" />
            <p>Loading budgets…</p>
          </div>
        ) : budgetPage.content.length === 0 ? (
          <div className="empty-state">
            <span className="empty-state__icon">
              <PiggyBank size={27} />
            </span>
            <h3>No budgets for this month</h3>
            <p>
              Add a category budget to begin comparing planned and actual
              spending.
            </p>
            <button
              className="button button--primary"
              disabled={availableCategories.length === 0}
              onClick={openCreateDialog}
              type="button"
            >
              <Plus size={18} />
              Add budget
            </button>
          </div>
        ) : (
          <>
            <div className="budget-list">
              {budgetPage.content.map((budget) => {
                const usage = usageByBudgetId.get(budget.id)
                const status = usage?.status || 'ON_TRACK'
                const usagePercentage = Number(
                  usage?.usagePercentage || 0,
                )
                const progressWidth = Math.min(
                  Math.max(usagePercentage, 0),
                  100,
                )

                return (
                  <article className="budget-row" key={budget.id}>
                    <div className="budget-row__identity">
                      <span className="budget-row__icon">
                        <PiggyBank size={21} />
                      </span>

                      <div>
                        <strong>{budget.categoryName}</strong>
                        <span>
                          Warning at{' '}
                          {budget.warningThresholdPercentage}%
                        </span>
                      </div>
                    </div>

                    <div className="budget-progress">
                      <div className="budget-progress__labels">
                        <span>
                          {formatCurrency(
                            Number(usage?.spentAmount || 0),
                            budgetUsage.currency || defaultCurrency,
                          )}{' '}
                          spent
                        </span>
                        <strong>
                          {formatPercentage(usagePercentage)}
                        </strong>
                      </div>

                      <div
                        aria-label={`${budget.categoryName} budget usage`}
                        aria-valuemax={100}
                        aria-valuemin={0}
                        aria-valuenow={progressWidth}
                        className="budget-progress__track"
                        role="progressbar"
                      >
                        <span
                          className={`budget-progress__value budget-progress__value--${status.toLowerCase()}`}
                          style={{ width: `${progressWidth}%` }}
                        />
                      </div>

                      <span className="budget-progress__remaining">
                        {formatCurrency(
                          Number(
                            usage?.remainingAmount ?? budget.amount,
                          ),
                          budgetUsage.currency || defaultCurrency,
                        )}{' '}
                        remaining
                      </span>
                    </div>

                    <div className="budget-row__limit">
                      <strong>
                        {formatCurrency(
                          budget.amount,
                          budgetUsage.currency || defaultCurrency,
                        )}
                      </strong>
                      <span>Monthly limit</span>

                      <span
                        className={`budget-status budget-status--${status.toLowerCase()}`}
                      >
                        {formatStatus(status)}
                      </span>
                    </div>

                    <button
                      aria-label={`Manage ${budget.categoryName} budget`}
                      className="button button--secondary budget-row__action"
                      onClick={() => void openEditDialog(budget.id)}
                      type="button"
                    >
                      <Pencil size={16} />
                      Manage
                    </button>
                  </article>
                )
              })}
            </div>

            {budgetPage.totalPages > 1 && (
              <footer className="pagination">
                <p>
                  Page {budgetPage.page + 1} of {budgetPage.totalPages}
                </p>

                <div className="pagination__actions">
                  <button
                    aria-label="Previous page"
                    className="icon-button pagination__button"
                    disabled={budgetPage.first}
                    onClick={() => changePage(currentPage - 1)}
                    type="button"
                  >
                    <ChevronLeft size={19} />
                  </button>

                  <button
                    aria-label="Next page"
                    className="icon-button pagination__button"
                    disabled={budgetPage.last}
                    onClick={() => changePage(currentPage + 1)}
                    type="button"
                  >
                    <ChevronRight size={19} />
                  </button>
                </div>
              </footer>
            )}
          </>
        )}
      </section>

      {dialogMode && (
        <div className="modal-backdrop">
          <section
            aria-labelledby="budget-dialog-title"
            aria-modal="true"
            className="budget-dialog"
            role="dialog"
          >
            <header className="budget-dialog__header">
              <div>
                <p className="eyebrow">
                  {dialogMode === 'create'
                    ? 'New monthly budget'
                    : 'Budget details'}
                </p>
                <h2 id="budget-dialog-title">
                  {dialogMode === 'create'
                    ? 'Add a budget'
                    : selectedBudget?.categoryName || 'Loading budget'}
                </h2>
              </div>

              <button
                aria-label="Close dialog"
                className="icon-button"
                onClick={closeDialog}
                type="button"
              >
                <X size={21} />
              </button>
            </header>

            {dialogLoading ? (
              <div className="budgets-loading budgets-loading--dialog">
                <span className="budgets-loading__spinner" />
                <p>Loading budget…</p>
              </div>
            ) : (
              <form className="budget-form" onSubmit={handleSubmit}>
                {formMessage && (
                  <div
                    className="form-message form-message--error"
                    role="alert"
                  >
                    {formMessage}
                  </div>
                )}

                <div className="budget-form-grid">
                  <label className="form-field">
                    <span>Category</span>
                    <select
                      disabled={dialogMode === 'edit'}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          categoryId: event.target.value,
                        }))
                      }
                      required
                      value={form.categoryId}
                    >
                      <option value="">Select category</option>

                      {dialogMode === 'edit' && selectedBudget ? (
                        <option value={selectedBudget.categoryId}>
                          {selectedBudget.categoryName}
                        </option>
                      ) : (
                        availableCategories.map((category) => (
                          <option key={category.id} value={category.id}>
                            {category.name}
                          </option>
                        ))
                      )}
                    </select>
                    {fieldErrors.categoryId && (
                      <small>{fieldErrors.categoryId}</small>
                    )}
                  </label>

                  <label className="form-field">
                    <span>Month</span>
                    <input
                      disabled={dialogMode === 'edit'}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          budgetMonth: event.target.value,
                        }))
                      }
                      required
                      type="month"
                      value={form.budgetMonth}
                    />
                    {fieldErrors.budgetMonth && (
                      <small>{fieldErrors.budgetMonth}</small>
                    )}
                  </label>

                  <label className="form-field">
                    <span>Budget amount</span>
                    <input
                      min="0.01"
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          amount: event.target.value,
                        }))
                      }
                      placeholder="500.00"
                      required
                      step="0.01"
                      type="number"
                      value={form.amount}
                    />
                    {fieldErrors.amount && (
                      <small>{fieldErrors.amount}</small>
                    )}
                  </label>

                  <label className="form-field">
                    <span>Warning threshold</span>
                    <div className="budget-threshold-input">
                      <input
                        max="99"
                        min="1"
                        onChange={(event) =>
                          setForm((current) => ({
                            ...current,
                            warningThresholdPercentage:
                              event.target.value,
                          }))
                        }
                        required
                        type="number"
                        value={form.warningThresholdPercentage}
                      />
                      <span>%</span>
                    </div>
                    {fieldErrors.warningThresholdPercentage && (
                      <small>
                        {fieldErrors.warningThresholdPercentage}
                      </small>
                    )}
                  </label>
                </div>

                <p className="budget-form__hint">
                  FinTrack creates a warning when processed spending reaches
                  the threshold and an exceeded alert when spending passes
                  the budget.
                </p>

                <footer className="budget-dialog__actions">
                  {dialogMode === 'edit' && selectedBudget && (
                    <button
                      className="button button--danger"
                      disabled={submitting}
                      onClick={() => void handleDeleteBudget()}
                      type="button"
                    >
                      Delete budget
                    </button>
                  )}

                  <div>
                    <button
                      className="button button--secondary"
                      disabled={submitting}
                      onClick={closeDialog}
                      type="button"
                    >
                      Cancel
                    </button>

                    <button
                      className="button button--primary"
                      disabled={submitting}
                      type="submit"
                    >
                      {submitting
                        ? 'Saving…'
                        : dialogMode === 'create'
                          ? 'Create budget'
                          : 'Save changes'}
                    </button>
                  </div>
                </footer>
              </form>
            )}
          </section>
        </div>
      )}
    </div>
  )
}

export default BudgetsPage