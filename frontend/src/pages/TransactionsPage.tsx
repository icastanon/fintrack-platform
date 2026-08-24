import {
  ArrowDownLeft,
  ArrowUpRight,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  Filter,
  Landmark,
  Plus,
  ReceiptText,
  RefreshCw,
  Store,
  Tag,
  X,
} from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/useAuth'
import { accountApi } from '../services/accountApi'
import { ApiError } from '../services/apiClient'
import { categoryApi } from '../services/categoryApi'
import { transactionApi } from '../services/transactionApi'
import type { FinancialAccount } from '../types/account'
import type { Category } from '../types/category'
import {
  PROCESSING_STATUSES,
  TRANSACTION_TYPES,
  type FinancialTransaction,
  type ProcessingStatus,
  type TransactionFilter,
  type TransactionPageResponse,
  type TransactionType,
} from '../types/transaction'

const PAGE_SIZE = 15

const EMPTY_PAGE: TransactionPageResponse = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

interface FilterForm {
  accountId: string
  categoryId: string
  transactionType: '' | TransactionType
  processingStatus: '' | ProcessingStatus
  fromDate: string
  toDate: string
}

interface CreateForm {
  accountId: string
  transactionType: TransactionType
  amount: string
  merchant: string
  description: string
  transactionDate: string
}

const EMPTY_FILTERS: FilterForm = {
  accountId: '',
  categoryId: '',
  transactionType: '',
  processingStatus: '',
  fromDate: '',
  toDate: '',
}

function getToday() {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function createEmptyTransactionForm(): CreateForm {
  return {
    accountId: '',
    transactionType: 'EXPENSE',
    amount: '',
    merchant: '',
    description: '',
    transactionDate: getToday(),
  }
}

function formatCurrency(
  value: number,
  currency: string,
  transactionType?: TransactionType,
) {
  const amount = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(Number(value))

  if (transactionType === 'EXPENSE') {
    return `−${amount}`
  }

  if (transactionType === 'INCOME') {
    return `+${amount}`
  }

  return amount
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function formatLabel(value: string) {
  return value
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ')
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

function toTransactionFilter(
  filters: FilterForm,
  page: number,
): TransactionFilter {
  return {
    accountId: filters.accountId
      ? Number(filters.accountId)
      : undefined,
    categoryId: filters.categoryId
      ? Number(filters.categoryId)
      : undefined,
    transactionType: filters.transactionType || undefined,
    processingStatus: filters.processingStatus || undefined,
    fromDate: filters.fromDate || undefined,
    toDate: filters.toDate || undefined,
    page,
    size: PAGE_SIZE,
  }
}

function TransactionsPage() {
  const { user } = useAuth()
  const currency = user?.currency || 'USD'

  const [transactionsPage, setTransactionsPage] =
    useState<TransactionPageResponse>(EMPTY_PAGE)
  const [accounts, setAccounts] = useState<FinancialAccount[]>([])
  const [categories, setCategories] = useState<Category[]>([])

  const [filterForm, setFilterForm] =
    useState<FilterForm>(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] =
    useState<FilterForm>(EMPTY_FILTERS)
  const [currentPage, setCurrentPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [resourceError, setResourceError] = useState<string | null>(null)
  const [pageMessage, setPageMessage] = useState<string | null>(null)

  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [createForm, setCreateForm] =
    useState<CreateForm>(createEmptyTransactionForm)
  const [createErrors, setCreateErrors] =
    useState<Record<string, string>>({})
  const [createMessage, setCreateMessage] = useState<string | null>(null)

  const [selectedTransaction, setSelectedTransaction] =
    useState<FinancialTransaction | null>(null)
  const [detailsDialogOpen, setDetailsDialogOpen] = useState(false)
  const [detailsLoading, setDetailsLoading] = useState(false)
  const [selectedCategoryId, setSelectedCategoryId] = useState('')
  const [detailsMessage, setDetailsMessage] = useState<string | null>(null)

  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let active = true

    Promise.all([
      accountApi.getAccounts(0, 100),
      categoryApi.getCategories(),
    ])
      .then(([accountPage, categoryList]) => {
        if (!active) {
          return
        }

        setAccounts(accountPage.content)
        setCategories(categoryList)
        setResourceError(null)
      })
      .catch((error: unknown) => {
        if (!active) {
          return
        }

        setResourceError(
          getErrorMessage(
            error,
            'Accounts and categories could not be loaded.',
          ),
        )
      })

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true

    transactionApi
      .getTransactions(
        toTransactionFilter(appliedFilters, currentPage),
      )
      .then((response) => {
        if (!active) {
          return
        }

        setTransactionsPage(response)
        setLoadError(null)
      })
      .catch((error: unknown) => {
        if (!active) {
          return
        }

        setLoadError(
          getErrorMessage(
            error,
            'Transactions could not be loaded.',
          ),
        )
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [appliedFilters, currentPage, reloadKey])

  function refreshTransactions() {
    setLoading(true)
    setReloadKey((current) => current + 1)
  }

  function handleApplyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPageMessage(null)

    if (
      filterForm.fromDate &&
      filterForm.toDate &&
      filterForm.fromDate > filterForm.toDate
    ) {
      setLoadError('The from date cannot be after the to date.')
      return
    }

    setLoading(true)
    setCurrentPage(0)
    setAppliedFilters({ ...filterForm })
  }

  function clearFilters() {
    setFilterForm(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
    setCurrentPage(0)
    setLoadError(null)
    setLoading(true)
  }

  function changePage(page: number) {
    setCurrentPage(page)
    setLoading(true)
  }

  function openCreateDialog() {
    const firstActiveAccount = accounts.find(
      (account) => account.status === 'ACTIVE',
    )

    setCreateForm({
      ...createEmptyTransactionForm(),
      accountId: firstActiveAccount
        ? String(firstActiveAccount.id)
        : '',
    })
    setCreateErrors({})
    setCreateMessage(null)
    setCreateDialogOpen(true)
  }

  function closeCreateDialog() {
    if (!submitting) {
      setCreateDialogOpen(false)
    }
  }

  async function handleCreateTransaction(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()
    setSubmitting(true)
    setCreateErrors({})
    setCreateMessage(null)

    try {
      await transactionApi.createTransaction({
        accountId: Number(createForm.accountId),
        transactionType: createForm.transactionType,
        amount: Number(createForm.amount),
        merchant: createForm.merchant.trim() || undefined,
        description: createForm.description.trim() || undefined,
        transactionDate: createForm.transactionDate,
      })

      setCreateDialogOpen(false)
      setPageMessage(
        'Transaction created. Its category and budget effects may take a few seconds to finish processing.',
      )
      setCurrentPage(0)
      refreshTransactions()
    } catch (error) {
      if (error instanceof ApiError) {
        setCreateErrors(error.fieldErrors)
        setCreateMessage(error.message)
      } else {
        setCreateMessage('The transaction could not be created.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function openTransactionDetails(transactionId: number) {
    setSelectedTransaction(null)
    setSelectedCategoryId('')
    setDetailsMessage(null)
    setDetailsDialogOpen(true)
    setDetailsLoading(true)

    try {
      const transaction =
        await transactionApi.getTransaction(transactionId)

      setSelectedTransaction(transaction)
      setSelectedCategoryId(
        transaction.categoryId
          ? String(transaction.categoryId)
          : '',
      )
    } catch (error) {
      setDetailsDialogOpen(false)
      setLoadError(
        getErrorMessage(
          error,
          'The transaction could not be loaded.',
        ),
      )
    } finally {
      setDetailsLoading(false)
    }
  }

  function closeDetailsDialog() {
    if (!submitting) {
      setDetailsDialogOpen(false)
      setSelectedTransaction(null)
    }
  }

  async function handleCategoryOverride(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (!selectedTransaction || !selectedCategoryId) {
      return
    }

    setSubmitting(true)
    setDetailsMessage(null)

    try {
      const updatedTransaction =
        await transactionApi.overrideCategory(
          selectedTransaction.id,
          Number(selectedCategoryId),
          selectedTransaction.version,
        )

      setSelectedTransaction(updatedTransaction)
      setDetailsMessage(
        'Category updated. Budget calculations will be processed asynchronously.',
      )
      setPageMessage('Transaction category updated.')
      refreshTransactions()
    } catch (error) {
      setDetailsMessage(
        getErrorMessage(
          error,
          'The transaction category could not be updated.',
        ),
      )
    } finally {
      setSubmitting(false)
    }
  }

  const activeAccounts = accounts.filter(
    (account) => account.status === 'ACTIVE',
  )

  return (
    <div className="transactions-page">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Financial activity</p>
          <h1>Transactions</h1>
          <p className="page-heading__description">
            Add transactions, monitor processing, and review or override
            automatic categorization.
          </p>
        </div>

        <div className="page-actions">
          <button
            className="button button--secondary"
            onClick={refreshTransactions}
            type="button"
          >
            <RefreshCw size={17} />
            Refresh
          </button>

          <button
            className="button button--primary"
            disabled={activeAccounts.length === 0}
            onClick={openCreateDialog}
            type="button"
          >
            <Plus size={18} />
            Add transaction
          </button>
        </div>
      </header>

      {resourceError && (
        <div className="form-message form-message--error" role="alert">
          {resourceError}
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

      <section className="content-card transaction-filter-card">
        <header className="transaction-filter-card__header">
          <Filter size={18} />
          <div>
            <h2>Filter transactions</h2>
            <p>Use any combination of the available filters.</p>
          </div>
        </header>

        <form
          className="transaction-filters"
          onSubmit={handleApplyFilters}
        >
          <label>
            Account
            <select
              onChange={(event) =>
                setFilterForm((current) => ({
                  ...current,
                  accountId: event.target.value,
                }))
              }
              value={filterForm.accountId}
            >
              <option value="">All accounts</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            Category
            <select
              onChange={(event) =>
                setFilterForm((current) => ({
                  ...current,
                  categoryId: event.target.value,
                }))
              }
              value={filterForm.categoryId}
            >
              <option value="">All categories</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            Type
            <select
              onChange={(event) =>
                setFilterForm((current) => ({
                  ...current,
                  transactionType:
                    event.target.value as '' | TransactionType,
                }))
              }
              value={filterForm.transactionType}
            >
              <option value="">All types</option>
              {TRANSACTION_TYPES.map((type) => (
                <option key={type} value={type}>
                  {formatLabel(type)}
                </option>
              ))}
            </select>
          </label>

          <label>
            Status
            <select
              onChange={(event) =>
                setFilterForm((current) => ({
                  ...current,
                  processingStatus:
                    event.target.value as '' | ProcessingStatus,
                }))
              }
              value={filterForm.processingStatus}
            >
              <option value="">All statuses</option>
              {PROCESSING_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {formatLabel(status)}
                </option>
              ))}
            </select>
          </label>

          <label>
            From
            <input
              onChange={(event) =>
                setFilterForm((current) => ({
                  ...current,
                  fromDate: event.target.value,
                }))
              }
              type="date"
              value={filterForm.fromDate}
            />
          </label>

          <label>
            To
            <input
              onChange={(event) =>
                setFilterForm((current) => ({
                  ...current,
                  toDate: event.target.value,
                }))
              }
              type="date"
              value={filterForm.toDate}
            />
          </label>

          <div className="transaction-filters__actions">
            <button
              className="button button--secondary"
              onClick={clearFilters}
              type="button"
            >
              Clear
            </button>
            <button className="button button--primary" type="submit">
              Apply filters
            </button>
          </div>
        </form>
      </section>

      <section className="content-card transactions-card">
        <header className="content-card__header">
          <div>
            <h2>Transaction history</h2>
            <p>
              {transactionsPage.totalElements} transaction
              {transactionsPage.totalElements === 1 ? '' : 's'} found.
            </p>
          </div>
        </header>

        {loading ? (
          <div className="accounts-loading">
            <span className="accounts-loading__spinner" />
            <p>Loading transactions…</p>
          </div>
        ) : transactionsPage.content.length === 0 ? (
          <div className="empty-state">
            <span className="empty-state__icon">
              <ReceiptText size={27} />
            </span>
            <h3>No transactions found</h3>
            <p>
              Add a transaction or adjust your filters to see financial
              activity.
            </p>
            {activeAccounts.length > 0 && (
              <button
                className="button button--primary"
                onClick={openCreateDialog}
                type="button"
              >
                <Plus size={18} />
                Add transaction
              </button>
            )}
          </div>
        ) : (
          <>
            <div className="transaction-list">
              {transactionsPage.content.map((transaction) => {
                const TypeIcon =
                  transaction.transactionType === 'INCOME'
                    ? ArrowDownLeft
                    : ArrowUpRight

                return (
                  <article
                    className="transaction-row"
                    key={transaction.id}
                  >
                    <div className="transaction-row__identity">
                      <span
                        className={`transaction-row__icon transaction-row__icon--${transaction.transactionType.toLowerCase()}`}
                      >
                        <TypeIcon size={20} />
                      </span>
                      <div>
                        <strong>
                          {transaction.merchant ||
                            transaction.description ||
                            'Transaction'}
                        </strong>
                        <span>
                          {formatDate(transaction.transactionDate)}
                        </span>
                      </div>
                    </div>

                    <div className="transaction-row__metadata">
                      <span>{transaction.accountName}</span>
                      <small>
                        {transaction.categoryName || 'Uncategorized'}
                      </small>
                    </div>

                    <div className="transaction-row__amount">
                      <strong
                        className={`transaction-amount transaction-amount--${transaction.transactionType.toLowerCase()}`}
                      >
                        {formatCurrency(
                          transaction.amount,
                          currency,
                          transaction.transactionType,
                        )}
                      </strong>
                      <span
                        className={`transaction-status transaction-status--${transaction.processingStatus.toLowerCase()}`}
                      >
                        {formatLabel(transaction.processingStatus)}
                      </span>
                    </div>

                    <button
                      className="button button--secondary transaction-row__action"
                      onClick={() =>
                        void openTransactionDetails(transaction.id)
                      }
                      type="button"
                    >
                      Details
                    </button>
                  </article>
                )
              })}
            </div>

            {transactionsPage.totalPages > 1 && (
              <footer className="pagination">
                <p>
                  Page {transactionsPage.page + 1} of{' '}
                  {transactionsPage.totalPages}
                </p>

                <div className="pagination__actions">
                  <button
                    aria-label="Previous page"
                    className="icon-button pagination__button"
                    disabled={transactionsPage.first}
                    onClick={() => changePage(currentPage - 1)}
                    type="button"
                  >
                    <ChevronLeft size={19} />
                  </button>

                  <button
                    aria-label="Next page"
                    className="icon-button pagination__button"
                    disabled={transactionsPage.last}
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

      {createDialogOpen && (
        <div className="modal-backdrop">
          <section
            aria-labelledby="create-transaction-title"
            aria-modal="true"
            className="transaction-dialog"
            role="dialog"
          >
            <header className="transaction-dialog__header">
              <div>
                <p className="eyebrow">Manual transaction</p>
                <h2 id="create-transaction-title">Add transaction</h2>
              </div>
              <button
                aria-label="Close dialog"
                className="icon-button"
                onClick={closeCreateDialog}
                type="button"
              >
                <X size={21} />
              </button>
            </header>

            <form
              className="transaction-dialog__form"
              onSubmit={handleCreateTransaction}
            >
              {createMessage && (
                <div
                  className="form-message form-message--error"
                  role="alert"
                >
                  {createMessage}
                </div>
              )}

              <div className="transaction-form-grid">
                <label className="form-field">
                  Account
                  <div className="form-control">
                    <Landmark size={18} />
                    <select
                      onChange={(event) =>
                        setCreateForm((current) => ({
                          ...current,
                          accountId: event.target.value,
                        }))
                      }
                      required
                      value={createForm.accountId}
                    >
                      <option value="">Select an account</option>
                      {activeAccounts.map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  {createErrors.accountId && (
                    <span className="field-error">
                      {createErrors.accountId}
                    </span>
                  )}
                </label>

                <label className="form-field">
                  Type
                  <div className="form-control">
                    <ReceiptText size={18} />
                    <select
                      onChange={(event) =>
                        setCreateForm((current) => ({
                          ...current,
                          transactionType:
                            event.target.value as TransactionType,
                        }))
                      }
                      value={createForm.transactionType}
                    >
                      {TRANSACTION_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {formatLabel(type)}
                        </option>
                      ))}
                    </select>
                  </div>
                </label>

                <label className="form-field">
                  Amount
                  <div className="form-control">
                    <CircleDollarSign size={18} />
                    <input
                      min="0.01"
                      onChange={(event) =>
                        setCreateForm((current) => ({
                          ...current,
                          amount: event.target.value,
                        }))
                      }
                      placeholder="0.00"
                      required
                      step="0.01"
                      type="number"
                      value={createForm.amount}
                    />
                  </div>
                  {createErrors.amount && (
                    <span className="field-error">
                      {createErrors.amount}
                    </span>
                  )}
                </label>

                <label className="form-field">
                  Date
                  <div className="form-control">
                    <CalendarDays size={18} />
                    <input
                      max={getToday()}
                      onChange={(event) =>
                        setCreateForm((current) => ({
                          ...current,
                          transactionDate: event.target.value,
                        }))
                      }
                      required
                      type="date"
                      value={createForm.transactionDate}
                    />
                  </div>
                  {createErrors.transactionDate && (
                    <span className="field-error">
                      {createErrors.transactionDate}
                    </span>
                  )}
                </label>
              </div>

              <label className="form-field">
                Merchant
                <div className="form-control">
                  <Store size={18} />
                  <input
                    maxLength={200}
                    onChange={(event) =>
                      setCreateForm((current) => ({
                        ...current,
                        merchant: event.target.value,
                      }))
                    }
                    placeholder="Optional merchant"
                    type="text"
                    value={createForm.merchant}
                  />
                </div>
                {createErrors.merchant && (
                  <span className="field-error">
                    {createErrors.merchant}
                  </span>
                )}
              </label>

              <label className="form-field">
                Description
                <textarea
                  className="transaction-textarea"
                  maxLength={500}
                  onChange={(event) =>
                    setCreateForm((current) => ({
                      ...current,
                      description: event.target.value,
                    }))
                  }
                  placeholder="Optional transaction description"
                  rows={3}
                  value={createForm.description}
                />
                {createErrors.description && (
                  <span className="field-error">
                    {createErrors.description}
                  </span>
                )}
              </label>

              <footer className="transaction-dialog__actions">
                <button
                  className="button button--secondary"
                  disabled={submitting}
                  onClick={closeCreateDialog}
                  type="button"
                >
                  Cancel
                </button>
                <button
                  className="button button--primary"
                  disabled={submitting}
                  type="submit"
                >
                  {submitting ? 'Creating…' : 'Create transaction'}
                </button>
              </footer>
            </form>
          </section>
        </div>
      )}

      {detailsDialogOpen && (
        <div className="modal-backdrop">
          <section
            aria-labelledby="transaction-details-title"
            aria-modal="true"
            className="transaction-dialog"
            role="dialog"
          >
            <header className="transaction-dialog__header">
              <div>
                <p className="eyebrow">Transaction details</p>
                <h2 id="transaction-details-title">
                  {selectedTransaction?.merchant ||
                    selectedTransaction?.description ||
                    'Transaction'}
                </h2>
              </div>
              <button
                aria-label="Close dialog"
                className="icon-button"
                onClick={closeDetailsDialog}
                type="button"
              >
                <X size={21} />
              </button>
            </header>

            {detailsLoading ? (
              <div className="accounts-loading accounts-loading--dialog">
                <span className="accounts-loading__spinner" />
                <p>Loading transaction…</p>
              </div>
            ) : (
              selectedTransaction && (
                <div className="transaction-dialog__form">
                  {detailsMessage && (
                    <div
                      className="form-message form-message--success"
                      role="status"
                    >
                      {detailsMessage}
                    </div>
                  )}

                  <div className="transaction-detail-grid">
                    <div>
                      <span>Amount</span>
                      <strong>
                        {formatCurrency(
                          selectedTransaction.amount,
                          currency,
                          selectedTransaction.transactionType,
                        )}
                      </strong>
                    </div>
                    <div>
                      <span>Date</span>
                      <strong>
                        {formatDate(
                          selectedTransaction.transactionDate,
                        )}
                      </strong>
                    </div>
                    <div>
                      <span>Account</span>
                      <strong>{selectedTransaction.accountName}</strong>
                    </div>
                    <div>
                      <span>Status</span>
                      <strong>
                        {formatLabel(
                          selectedTransaction.processingStatus,
                        )}
                      </strong>
                    </div>
                    <div>
                      <span>Source</span>
                      <strong>
                        {formatLabel(selectedTransaction.source)}
                      </strong>
                    </div>
                    <div>
                      <span>Category source</span>
                      <strong>
                        {selectedTransaction.manualCategoryOverride
                          ? 'Manual override'
                          : 'Automatic'}
                      </strong>
                    </div>
                  </div>

                  {selectedTransaction.description && (
                    <div className="transaction-description">
                      <span>Description</span>
                      <p>{selectedTransaction.description}</p>
                    </div>
                  )}

                  <form
                    className="category-override-form"
                    onSubmit={handleCategoryOverride}
                  >
                    <label className="form-field">
                      Category
                      <div className="form-control">
                        <Tag size={18} />
                        <select
                          onChange={(event) =>
                            setSelectedCategoryId(event.target.value)
                          }
                          required
                          value={selectedCategoryId}
                        >
                          <option value="">Select category</option>
                          {categories.map((category) => (
                            <option
                              key={category.id}
                              value={category.id}
                            >
                              {category.name}
                            </option>
                          ))}
                        </select>
                      </div>
                      <span className="field-hint">
                        Saving this selection prevents automatic
                        categorization from overwriting it.
                      </span>
                    </label>

                    <footer className="transaction-dialog__actions">
                      <button
                        className="button button--secondary"
                        disabled={submitting}
                        onClick={closeDetailsDialog}
                        type="button"
                      >
                        Close
                      </button>
                      <button
                        className="button button--primary"
                        disabled={!selectedCategoryId || submitting}
                        type="submit"
                      >
                        {submitting
                          ? 'Updating…'
                          : 'Update category'}
                      </button>
                    </footer>
                  </form>
                </div>
              )
            )}
          </section>
        </div>
      )}
    </div>
  )
}

export default TransactionsPage