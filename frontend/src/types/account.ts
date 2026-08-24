export const ACCOUNT_TYPES = [
  'CHECKING',
  'SAVINGS',
  'CREDIT_CARD',
  'CASH',
  'INVESTMENT',
] as const

export type AccountType = (typeof ACCOUNT_TYPES)[number]
export type AccountStatus = 'ACTIVE' | 'CLOSED'

export interface FinancialAccount {
  id: number
  name: string
  accountType: AccountType
  currency: string
  openingBalance: number
  currentBalance: number
  status: AccountStatus
  version: number
  createdAt: string
  updatedAt: string
}

export interface FinancialAccountCreateRequest {
  name: string
  accountType: AccountType
  openingBalance: number
}

export interface FinancialAccountUpdateRequest {
  name: string
  accountType: AccountType
  version: number
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}