import {
  AlertCircle,
  ArrowRight,
  CircleDollarSign,
  FileUp,
  Gauge,
  Landmark,
  LoaderCircle,
  Plus,
  ReceiptText,
  TrendingDown,
  TrendingUp,
  WalletCards,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useEffect, useState, type ChangeEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { accountApi } from '../services/accountApi'
import { ApiError } from '../services/apiClient'
import { summaryApi } from '../services/summaryApi'
import { transactionApi } from '../services/transactionApi'
import type { FinancialAccount } from '../types/account'
import type { MonthlyBudgetUsage } from '../types/budget'
import type {
  MonthlyAccountSpending,
  MonthlyCashFlow,
  MonthlyCategorySpending,
} from '../types/summary'
import type { FinancialTransaction } from '../types/transaction'

interface DashboardData {
  accounts: FinancialAccount[]
  transactions: FinancialTransaction[]
  cashFlow: MonthlyCashFlow
  categorySpending: MonthlyCategorySpending
  accountSpending: MonthlyAccountSpending
  budgetUsage: MonthlyBudgetUsage
}

interface SummaryCard {
  label: string
  value: string
  helperText: string
  icon: LucideIcon
  tone: string
}

const ACCOUNT_PAGE_SIZE = 100
const RECENT_TRANSACTION_COUNT = 5

function getCurrentMonth() {
  const currentDate = new Date()
  const month = String(currentDate.getMonth() + 1).padStart(2, '0')

  return `${currentDate.getFullYear()}-${month}`
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(Number(value))
}

function formatMonth(value: string) {
  const [year, month] = value.split('-').map(Number)

  return new Intl.DateTimeFormat(undefined, {
    month: 'long',
    year: 'numeric',
  }).format(new Date(year, month - 1, 1))
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function formatAccountType(value: string) {
  return value
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ')
}

function formatBudgetStatus(value: string) {
  return value
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ')
}

function getErrorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : 'The dashboard could not be loaded.'
}

function getTransactionLabel(transaction: FinancialTransaction) {
  return (
    transaction.merchant ||
    transaction.description ||
    (transaction.transactionType === 'INCOME' ? 'Income' : 'Expense')
  )
}

function getProgressWidth(value: number) {
  return Math.min(Math.max(Number(value), 0), 100)
}

async function getAllAccounts() {
  const firstPage = await accountApi.getAccounts(0, ACCOUNT_PAGE_SIZE)

  if (firstPage.totalPages <= 1) {
    return firstPage.content
  }

  const remainingPages = await Promise.all(
    Array.from(
      { length: firstPage.totalPages - 1 },
      (_, index) => accountApi.getAccounts(index + 1, ACCOUNT_PAGE_SIZE),
    ),
  )

  return [
    ...firstPage.content,
    ...remainingPages.flatMap((page) => page.content),
  ]
}

function DashboardPage() {
  const { user } = useAuth()
  const [selectedMonth, setSelectedMonth] = useState(getCurrentMonth)
  const [reloadKey, setReloadKey] = useState(0)
  const [dashboardData, setDashboardData] =
    useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    Promise.all([
      getAllAccounts(),
      transactionApi.getTransactions({
        page: 0,
        size: RECENT_TRANSACTION_COUNT,
      }),
      summaryApi.getCashFlow(selectedMonth),
      summaryApi.getSpendingByCategory(selectedMonth),
      summaryApi.getSpendingByAccount(selectedMonth),
      summaryApi.getBudgetUsage(selectedMonth),
    ])
      .then(
        ([
          accounts,
          transactionPage,
          cashFlow,
          categorySpending,
          accountSpending,
          budgetUsage,
        ]) => {
          if (!active) {
            return
          }

          setDashboardData({
            accounts,
            transactions: transactionPage.content,
            cashFlow,
            categorySpending,
            accountSpending,
            budgetUsage,
          })
          setLoadError(null)
        },
      )
      .catch((error: unknown) => {
        if (active) {
          setLoadError(getErrorMessage(error))
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
  }, [selectedMonth, reloadKey])

  function handleMonthChange(event: ChangeEvent<HTMLInputElement>) {
    setSelectedMonth(event.target.value)
    setLoading(true)
    setLoadError(null)
  }

  function retryLoading() {
    setLoading(true)
    setLoadError(null)
    setReloadKey((currentKey) => currentKey + 1)
  }

  if (loading && !dashboardData) {
    return (
      <div className="dashboard-page">
        <header className="page-heading">
          <div>
            <p className="eyebrow">Overview</p>
            <h1>Welcome back, {user?.username}</h1>
            <p className="page-heading__description">
              Loading your latest financial overview.
            </p>
          </div>
        </header>

        <section className="content-card dashboard-feedback">
          <LoaderCircle className="dashboard-spinner" size={30} />
          <h2>Loading dashboard</h2>
          <p>Your accounts, transactions, and summaries are being retrieved.</p>
        </section>
      </div>
    )
  }

  if (loadError && !dashboardData) {
    return (
      <div className="dashboard-page">
        <header className="page-heading">
          <div>
            <p className="eyebrow">Overview</p>
            <h1>Welcome back, {user?.username}</h1>
            <p className="page-heading__description">
              Your financial overview is temporarily unavailable.
            </p>
          </div>
        </header>

        <section className="content-card dashboard-feedback">
          <AlertCircle size={30} />
          <h2>Dashboard could not be loaded</h2>
          <p>{loadError}</p>
          <button
            className="button button--primary"
            onClick={retryLoading}
            type="button"
          >
            Try again
          </button>
        </section>
      </div>
    )
  }

  if (!dashboardData) {
    return null
  }

  const {
    accounts,
    transactions,
    cashFlow,
    categorySpending,
    accountSpending,
    budgetUsage,
  } = dashboardData

  const activeAccounts = accounts.filter(
    (account) => account.status === 'ACTIVE',
  )

  const totalBalance = activeAccounts.reduce(
    (total, account) => total + Number(account.currentBalance),
    0,
  )

  const budgetsNeedingAttention = budgetUsage.budgets.filter(
    (budget) => budget.status !== 'ON_TRACK',
  ).length

  const currency = cashFlow.currency
  const highestCategorySpending = Math.max(
    ...categorySpending.categories.map((item) => Number(item.spentAmount)),
    1,
  )
  const highestAccountSpending = Math.max(
    ...accountSpending.accounts.map((item) => Number(item.spentAmount)),
    1,
  )

  const orderedBudgets = [...budgetUsage.budgets].sort(
    (first, second) =>
      Number(second.usagePercentage) - Number(first.usagePercentage),
  )

  const summaryCards: SummaryCard[] = [
    {
      label: 'Total balance',
      value: formatCurrency(totalBalance, currency),
      helperText: `Across ${activeAccounts.length} active account${activeAccounts.length === 1 ? '' : 's'}`,
      icon: CircleDollarSign,
      tone: 'blue',
    },
    {
      label: 'Monthly income',
      value: formatCurrency(cashFlow.income, currency),
      helperText: `Processed during ${formatMonth(selectedMonth)}`,
      icon: TrendingUp,
      tone: 'green',
    },
    {
      label: 'Monthly expenses',
      value: formatCurrency(cashFlow.expenses, currency),
      helperText: `Net cash flow ${formatCurrency(cashFlow.netCashFlow, currency)}`,
      icon: TrendingDown,
      tone: 'red',
    },
    {
      label: 'Active budgets',
      value: String(budgetUsage.budgets.length),
      helperText:
        budgetsNeedingAttention === 0
          ? 'All budgets are on track'
          : `${budgetsNeedingAttention} need attention`,
      icon: Gauge,
      tone: 'purple',
    },
  ]

  return (
    <div className="dashboard-page">
      <header className="page-heading dashboard-heading">
        <div>
          <p className="eyebrow">Overview</p>
          <h1>Welcome back, {user?.username}</h1>
          <p className="page-heading__description">
            Monitor your accounts, recent activity, and monthly spending.
          </p>
        </div>

        <div className="page-actions dashboard-actions">
          <label className="dashboard-month-field">
            <span>Summary month</span>
            <input
              aria-label="Summary month"
              onChange={handleMonthChange}
              type="month"
              value={selectedMonth}
            />
          </label>

          <Link className="button button--secondary" to="/imports">
            <FileUp size={18} />
            <span>Import CSV</span>
          </Link>

          <Link className="button button--primary" to="/transactions">
            <Plus size={18} />
            <span>Add transaction</span>
          </Link>
        </div>
      </header>

      {loadError && (
        <div className="dashboard-inline-error" role="alert">
          <AlertCircle size={18} />
          <span>{loadError}</span>
          <button onClick={retryLoading} type="button">
            Retry
          </button>
        </div>
      )}

      <section className="summary-grid" aria-label="Financial summary">
        {summaryCards.map(
          ({ label, value, helperText, icon: Icon, tone }) => (
            <article className="summary-card" key={label}>
              <div
                className={`summary-card__icon summary-card__icon--${tone}`}
              >
                <Icon size={20} />
              </div>

              <p className="summary-card__label">{label}</p>
              <strong className="summary-card__value">{value}</strong>
              <p className="summary-card__helper">{helperText}</p>
            </article>
          ),
        )}
      </section>

      <div className="dashboard-grid">
        <section className="content-card">
          <header className="content-card__header">
            <div>
              <h2>Your accounts</h2>
              <p>Current balances across active accounts.</p>
            </div>

            <Link className="text-link" to="/accounts">
              View accounts
              <ArrowRight size={17} />
            </Link>
          </header>

          {activeAccounts.length === 0 ? (
            <div className="empty-state">
              <span className="empty-state__icon">
                <Landmark size={27} />
              </span>
              <h3>No active accounts</h3>
              <p>Create an account to begin tracking your finances.</p>
              <Link className="button button--secondary" to="/accounts">
                Manage accounts
              </Link>
            </div>
          ) : (
            <div className="dashboard-list">
              {activeAccounts.slice(0, 5).map((account) => (
                <div className="dashboard-list-row" key={account.id}>
                  <span className="dashboard-row-icon">
                    <WalletCards size={19} />
                  </span>

                  <div className="dashboard-row-copy">
                    <strong>{account.name}</strong>
                    <span>{formatAccountType(account.accountType)}</span>
                  </div>

                  <strong className="dashboard-row-value">
                    {formatCurrency(account.currentBalance, account.currency)}
                  </strong>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="content-card">
          <header className="content-card__header">
            <div>
              <h2>Recent transactions</h2>
              <p>Your latest financial activity.</p>
            </div>

            <Link className="text-link" to="/transactions">
              View all
              <ArrowRight size={17} />
            </Link>
          </header>

          {transactions.length === 0 ? (
            <div className="empty-state">
              <span className="empty-state__icon">
                <ReceiptText size={27} />
              </span>
              <h3>No transactions yet</h3>
              <p>Create or import a transaction to see activity here.</p>
              <Link
                className="button button--secondary"
                to="/transactions"
              >
                Add transaction
              </Link>
            </div>
          ) : (
            <div className="dashboard-list">
              {transactions.map((transaction) => (
                <div className="dashboard-list-row" key={transaction.id}>
                  <span
                    className={`dashboard-row-icon dashboard-row-icon--${transaction.transactionType.toLowerCase()}`}
                  >
                    {transaction.transactionType === 'INCOME' ? (
                      <TrendingUp size={19} />
                    ) : (
                      <TrendingDown size={19} />
                    )}
                  </span>

                  <div className="dashboard-row-copy">
                    <strong>{getTransactionLabel(transaction)}</strong>
                    <span>
                      {transaction.categoryName || 'Uncategorized'} ·{' '}
                      {formatDate(transaction.transactionDate)}
                    </span>
                  </div>

                  <div className="dashboard-transaction-value">
                    <strong
                      className={
                        transaction.transactionType === 'INCOME'
                          ? 'dashboard-money--income'
                          : 'dashboard-money--expense'
                      }
                    >
                      {transaction.transactionType === 'INCOME' ? '+' : '-'}
                      {formatCurrency(transaction.amount, currency)}
                    </strong>
                    <span>{transaction.processingStatus}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="content-card dashboard-card--wide">
          <header className="content-card__header">
            <div>
              <h2>Monthly spending</h2>
              <p>
                How processed expenses were distributed during{' '}
                {formatMonth(selectedMonth)}.
              </p>
            </div>

            <Link className="text-link" to="/transactions">
              Explore transactions
              <ArrowRight size={17} />
            </Link>
          </header>

          <div className="dashboard-spending-grid">
            <div className="dashboard-breakdown">
              <div className="dashboard-breakdown__heading">
                <div>
                  <span>By category</span>
                  <strong>
                    {formatCurrency(
                      categorySpending.totalExpenses,
                      categorySpending.currency,
                    )}
                  </strong>
                </div>
              </div>

              {categorySpending.categories.length === 0 ? (
                <p className="dashboard-empty-copy">
                  No processed expenses for this month.
                </p>
              ) : (
                <div className="dashboard-breakdown-list">
                  {categorySpending.categories.slice(0, 5).map((item) => (
                    <div
                      className="dashboard-breakdown-item"
                      key={item.categoryId}
                    >
                      <div className="dashboard-breakdown-label">
                        <span>{item.categoryName}</span>
                        <strong>
                          {formatCurrency(
                            item.spentAmount,
                            categorySpending.currency,
                          )}
                        </strong>
                      </div>

                      <div className="dashboard-breakdown-track">
                        <span
                          style={{
                            width: `${getProgressWidth(
                              (Number(item.spentAmount) /
                                highestCategorySpending) *
                                100,
                            )}%`,
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="dashboard-breakdown">
              <div className="dashboard-breakdown__heading">
                <div>
                  <span>By account</span>
                  <strong>
                    {formatCurrency(
                      accountSpending.totalExpenses,
                      accountSpending.currency,
                    )}
                  </strong>
                </div>
              </div>

              {accountSpending.accounts.length === 0 ? (
                <p className="dashboard-empty-copy">
                  No processed account spending for this month.
                </p>
              ) : (
                <div className="dashboard-breakdown-list">
                  {accountSpending.accounts.slice(0, 5).map((item) => (
                    <div
                      className="dashboard-breakdown-item"
                      key={item.accountId}
                    >
                      <div className="dashboard-breakdown-label">
                        <span>{item.accountName}</span>
                        <strong>
                          {formatCurrency(
                            item.spentAmount,
                            accountSpending.currency,
                          )}
                        </strong>
                      </div>

                      <div className="dashboard-breakdown-track">
                        <span
                          style={{
                            width: `${getProgressWidth(
                              (Number(item.spentAmount) /
                                highestAccountSpending) *
                                100,
                            )}%`,
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="content-card dashboard-card--wide">
          <header className="content-card__header">
            <div>
              <h2>Budget health</h2>
              <p>
                Current budget usage for {formatMonth(selectedMonth)}.
              </p>
            </div>

            <Link className="text-link" to="/budgets">
              Manage budgets
              <ArrowRight size={17} />
            </Link>
          </header>

          {orderedBudgets.length === 0 ? (
            <div className="empty-state dashboard-budget-empty">
              <span className="empty-state__icon">
                <Gauge size={27} />
              </span>
              <h3>No budgets for this month</h3>
              <p>Create a monthly budget to monitor category spending.</p>
              <Link className="button button--secondary" to="/budgets">
                Create budget
              </Link>
            </div>
          ) : (
            <div className="dashboard-budget-list">
              {orderedBudgets.slice(0, 6).map((budget) => (
                <div className="dashboard-budget-row" key={budget.budgetId}>
                  <div className="dashboard-budget-heading">
                    <div>
                      <strong>{budget.categoryName}</strong>
                      <span>
                        {formatCurrency(
                          budget.spentAmount,
                          budgetUsage.currency,
                        )}{' '}
                        of{' '}
                        {formatCurrency(
                          budget.budgetAmount,
                          budgetUsage.currency,
                        )}
                      </span>
                    </div>

                    <span
                      className={`dashboard-budget-status dashboard-budget-status--${budget.status.toLowerCase()}`}
                    >
                      {formatBudgetStatus(budget.status)}
                    </span>
                  </div>

                  <div className="dashboard-budget-track">
                    <span
                      className={`dashboard-budget-progress dashboard-budget-progress--${budget.status.toLowerCase()}`}
                      style={{
                        width: `${getProgressWidth(
                          budget.usagePercentage,
                        )}%`,
                      }}
                    />
                  </div>

                  <div className="dashboard-budget-details">
                    <span>
                      {Number(budget.usagePercentage).toFixed(2)}% used
                    </span>
                    <span>
                      {formatCurrency(
                        budget.remainingAmount,
                        budgetUsage.currency,
                      )}{' '}
                      remaining
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

export default DashboardPage