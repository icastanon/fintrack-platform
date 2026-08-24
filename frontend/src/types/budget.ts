export type BudgetStatus = 'ON_TRACK' | 'WARNING' | 'EXCEEDED'

export interface Budget {
  id: number
  categoryId: number
  categoryName: string
  budgetMonth: string
  amount: number
  warningThresholdPercentage: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface BudgetPageResponse {
  content: Budget[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface BudgetCreateRequest {
  categoryId: number
  budgetMonth: string
  amount: number
  warningThresholdPercentage: number
}

export interface BudgetUpdateRequest {
  amount: number
  warningThresholdPercentage: number
  version: number
}

export interface BudgetUsage {
  budgetId: number
  categoryId: number
  categoryName: string
  budgetAmount: number
  warningThresholdPercentage: number
  spentAmount: number
  remainingAmount: number
  usagePercentage: number
  status: BudgetStatus
}

export interface MonthlyBudgetUsage {
  month: string
  currency: string
  budgets: BudgetUsage[]
}