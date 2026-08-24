export type TransactionImportStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'ABANDONED'

export interface TransactionImport {
  id: number
  accountId: number
  accountName: string
  originalFileName: string
  contentType: string
  fileSizeBytes: number
  status: TransactionImportStatus
  totalRows: number | null
  processedRows: number | null
  successfulRows: number | null
  skippedRows: number | null
  failedRows: number | null
  failureSummary: string | null
  rejectedOutputAvailable: boolean
  version: number
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}