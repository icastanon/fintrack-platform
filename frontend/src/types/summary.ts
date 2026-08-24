import type { SupportedCurrency } from './auth'

export interface MonthlyCashFlow {
  month: string
  income: number
  expenses: number
  netCashFlow: number
  currency: SupportedCurrency
}

export interface CategorySpending {
  categoryId: number
  categoryName: string
  spentAmount: number
}

export interface MonthlyCategorySpending {
  month: string
  totalExpenses: number
  currency: SupportedCurrency
  categories: CategorySpending[]
}

export interface AccountSpending {
  accountId: number
  accountName: string
  spentAmount: number
}

export interface MonthlyAccountSpending {
  month: string
  totalExpenses: number
  currency: SupportedCurrency
  accounts: AccountSpending[]
}