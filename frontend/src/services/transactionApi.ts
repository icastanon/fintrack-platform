import type {
  FinancialTransaction,
  FinancialTransactionCreateRequest,
  TransactionFilter,
  TransactionPageResponse,
} from '../types/transaction'
import { apiRequest } from './apiClient'

function buildFilterQuery(filter: TransactionFilter) {
  const parameters = new URLSearchParams()

  Object.entries(filter).forEach(([name, value]) => {
    if (value !== undefined && value !== '') {
      parameters.set(name, String(value))
    }
  })

  return parameters.toString()
}

export const transactionApi = {
  getTransactions(filter: TransactionFilter = {}) {
    const query = buildFilterQuery(filter)

    return apiRequest<TransactionPageResponse>(
      `/api/v1/transactions${query ? `?${query}` : ''}`,
    )
  },

  getTransaction(transactionId: number) {
    return apiRequest<FinancialTransaction>(
      `/api/v1/transactions/${transactionId}`,
    )
  },

  createTransaction(request: FinancialTransactionCreateRequest) {
    return apiRequest<FinancialTransaction>('/api/v1/transactions', {
      method: 'POST',
      body: JSON.stringify(request),
    })
  },

  overrideCategory(
    transactionId: number,
    categoryId: number,
    version: number,
  ) {
    return apiRequest<FinancialTransaction>(
      `/api/v1/transactions/${transactionId}/category`,
      {
        method: 'PATCH',
        body: JSON.stringify({ categoryId, version }),
      },
    )
  },
}