import type {
  FinancialAccount,
  FinancialAccountCreateRequest,
  FinancialAccountUpdateRequest,
  PageResponse,
} from '../types/account'
import { apiRequest } from './apiClient'

export const accountApi = {
  getAccounts(page = 0, size = 10) {
    return apiRequest<PageResponse<FinancialAccount>>(
      `/api/v1/accounts?page=${page}&size=${size}`,
    )
  },

  getAccount(accountId: number) {
    return apiRequest<FinancialAccount>(`/api/v1/accounts/${accountId}`)
  },

  createAccount(request: FinancialAccountCreateRequest) {
    return apiRequest<FinancialAccount>('/api/v1/accounts', {
      method: 'POST',
      body: JSON.stringify(request),
    })
  },

  updateAccount(
    accountId: number,
    request: FinancialAccountUpdateRequest,
  ) {
    return apiRequest<FinancialAccount>(
      `/api/v1/accounts/${accountId}`,
      {
        method: 'PUT',
        body: JSON.stringify(request),
      },
    )
  },

  closeAccount(accountId: number) {
    return apiRequest<FinancialAccount>(
      `/api/v1/accounts/${accountId}/close`,
      {
        method: 'PATCH',
      },
    )
  },
}