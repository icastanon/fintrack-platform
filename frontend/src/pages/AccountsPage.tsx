import {
  Banknote,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  CreditCard,
  Landmark,
  Pencil,
  PiggyBank,
  Plus,
  WalletCards,
  X,
} from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { accountApi } from '../services/accountApi'
import { ApiError } from '../services/apiClient'
import {
  ACCOUNT_TYPES,
  type AccountType,
  type FinancialAccount,
  type PageResponse,
} from '../types/account'

const PAGE_SIZE = 10

const EMPTY_PAGE: PageResponse<FinancialAccount> = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

interface AccountFormState {
  name: string
  accountType: AccountType
  openingBalance: string
}

const EMPTY_FORM: AccountFormState = {
  name: '',
  accountType: 'CHECKING',
  openingBalance: '0.00',
}

type DialogMode = 'create' | 'edit' | null

const accountIcons = {
  CHECKING: Landmark,
  SAVINGS: PiggyBank,
  CREDIT_CARD: CreditCard,
  CASH: Banknote,
  INVESTMENT: CircleDollarSign,
}

function formatAccountType(accountType: AccountType) {
  return accountType
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ')
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(Number(value))
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value))
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

function AccountsPage() {
  const [accountsPage, setAccountsPage] =
    useState<PageResponse<FinancialAccount>>(EMPTY_PAGE)
  const [currentPage, setCurrentPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [dialogMode, setDialogMode] = useState<DialogMode>(null)
  const [dialogLoading, setDialogLoading] = useState(false)
  const [selectedAccount, setSelectedAccount] =
    useState<FinancialAccount | null>(null)
  const [form, setForm] = useState<AccountFormState>(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formMessage, setFormMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let active = true

    accountApi
      .getAccounts(currentPage, PAGE_SIZE)
      .then((response) => {
        if (!active) {
          return
        }

        setAccountsPage(response)
        setLoadError(null)
      })
      .catch((error: unknown) => {
        if (!active) {
          return
        }

        setLoadError(
          getErrorMessage(error, 'Accounts could not be loaded.'),
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
  }, [currentPage])

  async function refreshAccounts(page: number) {
    try {
      const response = await accountApi.getAccounts(page, PAGE_SIZE)
      setAccountsPage(response)
      setLoadError(null)
    } catch (error) {
      setLoadError(
        getErrorMessage(error, 'Accounts could not be loaded.'),
      )
    } finally {
      setLoading(false)
    }
  }

  function openCreateDialog() {
    setSelectedAccount(null)
    setForm(EMPTY_FORM)
    setFieldErrors({})
    setFormMessage(null)
    setDialogMode('create')
  }

  async function openEditDialog(accountId: number) {
    setDialogMode('edit')
    setDialogLoading(true)
    setSelectedAccount(null)
    setFieldErrors({})
    setFormMessage(null)

    try {
      const account = await accountApi.getAccount(accountId)

      setSelectedAccount(account)
      setForm({
        name: account.name,
        accountType: account.accountType,
        openingBalance: String(account.openingBalance),
      })
    } catch (error) {
      setDialogMode(null)
      setLoadError(
        getErrorMessage(error, 'The account could not be loaded.'),
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
    setSelectedAccount(null)
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
        await accountApi.createAccount({
          name: form.name.trim(),
          accountType: form.accountType,
          openingBalance: Number(form.openingBalance),
        })
      } else if (selectedAccount) {
        await accountApi.updateAccount(selectedAccount.id, {
          name: form.name.trim(),
          accountType: form.accountType,
          version: selectedAccount.version,
        })
      }

      setDialogMode(null)
      setSelectedAccount(null)
      setLoading(true)
      await refreshAccounts(currentPage)
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors)
        setFormMessage(error.message)
      } else {
        setFormMessage('The account could not be saved.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCloseAccount() {
    if (!selectedAccount) {
      return
    }

    const confirmed = window.confirm(
      `Close "${selectedAccount.name}"? Closed accounts cannot receive new transactions.`,
    )

    if (!confirmed) {
      return
    }

    setSubmitting(true)
    setFormMessage(null)

    try {
      await accountApi.closeAccount(selectedAccount.id)
      setDialogMode(null)
      setSelectedAccount(null)
      setLoading(true)
      await refreshAccounts(currentPage)
    } catch (error) {
      setFormMessage(
        getErrorMessage(error, 'The account could not be closed.'),
      )
    } finally {
      setSubmitting(false)
    }
  }

  function changePage(page: number) {
    setLoading(true)
    setCurrentPage(page)
  }

  const activeAccounts = accountsPage.content.filter(
    (account) => account.status === 'ACTIVE',
  ).length

  return (
    <div className="accounts-page">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Financial accounts</p>
          <h1>Accounts</h1>
          <p className="page-heading__description">
            Manage the accounts whose balances are updated by your transactions
            and CSV imports.
          </p>
        </div>

        <div className="page-actions">
          <button
            className="button button--primary"
            onClick={openCreateDialog}
            type="button"
          >
            <Plus size={18} />
            <span>Add account</span>
          </button>
        </div>
      </header>

      <section className="account-overview" aria-label="Account overview">
        <article className="account-overview__item">
          <span className="account-overview__icon">
            <WalletCards size={20} />
          </span>
          <div>
            <strong>{accountsPage.totalElements}</strong>
            <span>Total accounts</span>
          </div>
        </article>

        <article className="account-overview__item">
          <span className="account-overview__icon account-overview__icon--green">
            <Landmark size={20} />
          </span>
          <div>
            <strong>{activeAccounts}</strong>
            <span>Active on this page</span>
          </div>
        </article>
      </section>

      {loadError && (
        <div className="form-message form-message--error" role="alert">
          {loadError}
        </div>
      )}

      <section className="content-card accounts-card">
        <header className="content-card__header">
          <div>
            <h2>Your financial accounts</h2>
            <p>
              Select an account to review its details or change its information.
            </p>
          </div>
        </header>

        {loading ? (
          <div className="accounts-loading">
            <span className="accounts-loading__spinner" />
            <p>Loading accounts…</p>
          </div>
        ) : accountsPage.content.length === 0 ? (
          <div className="empty-state">
            <span className="empty-state__icon">
              <Landmark size={27} />
            </span>
            <h3>No accounts yet</h3>
            <p>
              Create your first account before adding or importing
              transactions.
            </p>
            <button
              className="button button--primary"
              onClick={openCreateDialog}
              type="button"
            >
              <Plus size={18} />
              Add account
            </button>
          </div>
        ) : (
          <>
            <div className="account-list">
              {accountsPage.content.map((account) => {
                const AccountIcon = accountIcons[account.accountType]

                return (
                  <article className="account-row" key={account.id}>
                    <div className="account-row__identity">
                      <span className="account-row__icon">
                        <AccountIcon size={21} />
                      </span>

                      <div>
                        <strong>{account.name}</strong>
                        <span>{formatAccountType(account.accountType)}</span>
                      </div>
                    </div>

                    <div className="account-row__balance">
                      <strong>
                        {formatCurrency(
                          account.currentBalance,
                          account.currency,
                        )}
                      </strong>
                      <span>Current balance</span>
                    </div>

                    <span
                      className={`status-badge status-badge--${account.status.toLowerCase()}`}
                    >
                      {account.status === 'ACTIVE' ? 'Active' : 'Closed'}
                    </span>

                    <button
                      aria-label={`Manage ${account.name}`}
                      className="button button--secondary account-row__action"
                      onClick={() => void openEditDialog(account.id)}
                      type="button"
                    >
                      <Pencil size={16} />
                      Manage
                    </button>
                  </article>
                )
              })}
            </div>

            {accountsPage.totalPages > 1 && (
              <footer className="pagination">
                <p>
                  Page {accountsPage.page + 1} of {accountsPage.totalPages}
                </p>

                <div className="pagination__actions">
                  <button
                    aria-label="Previous page"
                    className="icon-button pagination__button"
                    disabled={accountsPage.first}
                    onClick={() => changePage(currentPage - 1)}
                    type="button"
                  >
                    <ChevronLeft size={19} />
                  </button>

                  <button
                    aria-label="Next page"
                    className="icon-button pagination__button"
                    disabled={accountsPage.last}
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
            aria-labelledby="account-dialog-title"
            aria-modal="true"
            className="account-dialog"
            role="dialog"
          >
            <header className="account-dialog__header">
              <div>
                <p className="eyebrow">
                  {dialogMode === 'create'
                    ? 'New financial account'
                    : 'Account details'}
                </p>
                <h2 id="account-dialog-title">
                  {dialogMode === 'create'
                    ? 'Add an account'
                    : selectedAccount?.name || 'Loading account'}
                </h2>
              </div>

              <button
                aria-label="Close dialog"
                className="icon-button account-dialog__close"
                onClick={closeDialog}
                type="button"
              >
                <X size={21} />
              </button>
            </header>

            {dialogLoading ? (
              <div className="accounts-loading accounts-loading--dialog">
                <span className="accounts-loading__spinner" />
                <p>Loading account…</p>
              </div>
            ) : (
              <form className="account-form" onSubmit={handleSubmit}>
                {formMessage && (
                  <div
                    className="form-message form-message--error"
                    role="alert"
                  >
                    {formMessage}
                  </div>
                )}

                {selectedAccount && (
                  <div className="account-detail-grid">
                    <div>
                      <span>Current balance</span>
                      <strong>
                        {formatCurrency(
                          selectedAccount.currentBalance,
                          selectedAccount.currency,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Opening balance</span>
                      <strong>
                        {formatCurrency(
                          selectedAccount.openingBalance,
                          selectedAccount.currency,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Status</span>
                      <strong>
                        {selectedAccount.status === 'ACTIVE'
                          ? 'Active'
                          : 'Closed'}
                      </strong>
                    </div>

                    <div>
                      <span>Created</span>
                      <strong>{formatDate(selectedAccount.createdAt)}</strong>
                    </div>
                  </div>
                )}

                <label className="form-field">
                  Account name
                  <div className="form-control">
                    <Landmark size={18} />
                    <input
                      autoFocus
                      maxLength={100}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          name: event.target.value,
                        }))
                      }
                      placeholder="Everyday checking"
                      required
                      type="text"
                      value={form.name}
                    />
                  </div>
                  {fieldErrors.name && (
                    <span className="field-error">{fieldErrors.name}</span>
                  )}
                </label>

                <label className="form-field">
                  Account type
                  <div className="form-control">
                    <WalletCards size={18} />
                    <select
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          accountType: event.target.value as AccountType,
                        }))
                      }
                      value={form.accountType}
                    >
                      {ACCOUNT_TYPES.map((accountType) => (
                        <option key={accountType} value={accountType}>
                          {formatAccountType(accountType)}
                        </option>
                      ))}
                    </select>
                  </div>
                  {fieldErrors.accountType && (
                    <span className="field-error">
                      {fieldErrors.accountType}
                    </span>
                  )}
                </label>

                {dialogMode === 'create' && (
                  <label className="form-field">
                    Opening balance
                    <div className="form-control">
                      <CircleDollarSign size={18} />
                      <input
                        onChange={(event) =>
                          setForm((current) => ({
                            ...current,
                            openingBalance: event.target.value,
                          }))
                        }
                        required
                        step="0.01"
                        type="number"
                        value={form.openingBalance}
                      />
                    </div>
                    {fieldErrors.openingBalance && (
                      <span className="field-error">
                        {fieldErrors.openingBalance}
                      </span>
                    )}
                    <span className="field-hint">
                      This becomes the account’s initial tracked balance.
                    </span>
                  </label>
                )}

                <footer className="account-dialog__actions">
                  {selectedAccount?.status === 'ACTIVE' && (
                    <button
                      className="button button--danger"
                      disabled={submitting}
                      onClick={() => void handleCloseAccount()}
                      type="button"
                    >
                      Close account
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
                      disabled={
                        submitting || selectedAccount?.status === 'CLOSED'
                      }
                      type="submit"
                    >
                      {submitting
                        ? 'Saving…'
                        : dialogMode === 'create'
                          ? 'Create account'
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

export default AccountsPage