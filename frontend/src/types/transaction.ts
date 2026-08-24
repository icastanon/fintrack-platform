export const TRANSACTION_TYPES = ['INCOME', 'EXPENSE'] as const
export const PROCESSING_STATUSES = [
  'PENDING',
  'PROCESSED',
  'FAILED',
] as const

export type TransactionType = (typeof TRANSACTION_TYPES)[number]
export type ProcessingStatus = (typeof PROCESSING_STATUSES)[number]
export type TransactionSource = 'MANUAL' | 'IMPORT'

export interface FinancialTransaction {
  id: number
  accountId: number
  accountName: string
  categoryId: number | null
  categoryName: string | null
  transactionType: TransactionType
  amount: number
  merchant: string | null
  description: string | null
  transactionDate: string
  processingStatus: ProcessingStatus
  source: TransactionSource
  manualCategoryOverride: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface FinancialTransactionCreateRequest {
  accountId: number
  transactionType: TransactionType
  amount: number
  merchant?: string
  description?: string
  transactionDate: string
}

export interface TransactionFilter {
  accountId?: number
  categoryId?: number
  transactionType?: TransactionType
  processingStatus?: ProcessingStatus
  fromDate?: string
  toDate?: string
  page?: number
  size?: number
}

export interface TransactionPageResponse {
  content: FinancialTransaction[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}