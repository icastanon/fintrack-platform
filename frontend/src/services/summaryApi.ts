import type { MonthlyBudgetUsage } from '../types/budget'
import type {
  MonthlyAccountSpending,
  MonthlyCashFlow,
  MonthlyCategorySpending,
} from '../types/summary'
import { apiRequest } from './apiClient'

function buildMonthQuery(month: string) {
  return new URLSearchParams({ month }).toString()
}

export const summaryApi = {
  getCashFlow(month: string) {
    return apiRequest<MonthlyCashFlow>(
      `/api/v1/summaries/cash-flow?${buildMonthQuery(month)}`,
    )
  },

  getSpendingByCategory(month: string) {
    return apiRequest<MonthlyCategorySpending>(
      `/api/v1/summaries/spending-by-category?${buildMonthQuery(month)}`,
    )
  },

  getSpendingByAccount(month: string) {
    return apiRequest<MonthlyAccountSpending>(
      `/api/v1/summaries/spending-by-account?${buildMonthQuery(month)}`,
    )
  },

  getBudgetUsage(month: string) {
    return apiRequest<MonthlyBudgetUsage>(
      `/api/v1/summaries/budget-usage?${buildMonthQuery(month)}`,
    )
  },
}