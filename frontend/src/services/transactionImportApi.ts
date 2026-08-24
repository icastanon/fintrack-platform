import type { TransactionImport } from '../types/transactionImport'
import { apiDownload, apiRequest } from './apiClient'

export const transactionImportApi = {
  submitImport(accountId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)

    return apiRequest<TransactionImport>(
      `/api/v1/imports?accountId=${encodeURIComponent(String(accountId))}`,
      {
        method: 'POST',
        body: formData,
      },
    )
  },

  getImport(importId: number) {
    return apiRequest<TransactionImport>(
      `/api/v1/imports/${importId}`,
    )
  },

  getRejectedOutput(importId: number) {
    return apiDownload(
      `/api/v1/imports/${importId}/rejected-output`,
      `transaction-import-${importId}-rejected.csv`,
    )
  },
}