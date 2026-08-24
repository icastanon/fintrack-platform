import type {
  Budget,
  BudgetCreateRequest,
  BudgetPageResponse,
  BudgetUpdateRequest,
  MonthlyBudgetUsage,
} from '../types/budget'
import { apiRequest } from './apiClient'

export const budgetApi = {
  getBudgets(budgetMonth: string, page = 0, size = 12) {
    const parameters = new URLSearchParams({
      budgetMonth,
      page: String(page),
      size: String(size),
    })

    return apiRequest<BudgetPageResponse>(
      `/api/v1/budgets?${parameters.toString()}`,
    )
  },

  getBudget(budgetId: number) {
    return apiRequest<Budget>(`/api/v1/budgets/${budgetId}`)
  },

  createBudget(request: BudgetCreateRequest) {
    return apiRequest<Budget>('/api/v1/budgets', {
      method: 'POST',
      body: JSON.stringify(request),
    })
  },

  updateBudget(budgetId: number, request: BudgetUpdateRequest) {
    return apiRequest<Budget>(`/api/v1/budgets/${budgetId}`, {
      method: 'PUT',
      body: JSON.stringify(request),
    })
  },

  deleteBudget(budgetId: number, version: number) {
    return apiRequest<void>(
      `/api/v1/budgets/${budgetId}?version=${version}`,
      { method: 'DELETE' },
    )
  },

  getBudgetUsage(month: string) {
    const parameters = new URLSearchParams({ month })

    return apiRequest<MonthlyBudgetUsage>(
      `/api/v1/summaries/budget-usage?${parameters.toString()}`,
    )
  },
}