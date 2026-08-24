import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Download,
  FileSpreadsheet,
  Landmark,
  LoaderCircle,
  RefreshCw,
  Rows3,
  Search,
  UploadCloud,
  XCircle,
} from 'lucide-react'
import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from 'react'
import { accountApi } from '../services/accountApi'
import { ApiError } from '../services/apiClient'
import { transactionImportApi } from '../services/transactionImportApi'
import type { FinancialAccount } from '../types/account'
import type {
  TransactionImport,
  TransactionImportStatus,
} from '../types/transactionImport'

const TERMINAL_STATUSES = new Set<TransactionImportStatus>([
  'COMPLETED',
  'FAILED',
  'ABANDONED',
])

const STATUS_DETAILS = {
  QUEUED: {
    label: 'Queued',
    description: 'Waiting for a worker to begin processing.',
    Icon: Clock3,
  },
  RUNNING: {
    label: 'Running',
    description: 'The worker is currently processing this file.',
    Icon: LoaderCircle,
  },
  COMPLETED: {
    label: 'Completed',
    description: 'The import finished processing.',
    Icon: CheckCircle2,
  },
  FAILED: {
    label: 'Failed',
    description: 'The import stopped because of a processing failure.',
    Icon: XCircle,
  },
  ABANDONED: {
    label: 'Abandoned',
    description: 'The import exhausted its recovery window.',
    Icon: AlertTriangle,
  },
} satisfies Record<
  TransactionImportStatus,
  {
    label: string
    description: string
    Icon: typeof Clock3
  }
>

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }

  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDateTime(value: string | null) {
  if (!value) {
    return 'Not available'
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function count(value: number | null) {
  return value ?? 0
}

function ImportsPage() {
  const [accounts, setAccounts] = useState<FinancialAccount[]>([])
  const [accountsLoading, setAccountsLoading] = useState(true)
  const [accountsError, setAccountsError] = useState<string | null>(null)

  const [accountId, setAccountId] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  const [lookupId, setLookupId] = useState('')
  const [lookupLoading, setLookupLoading] = useState(false)
  const [lookupError, setLookupError] = useState<string | null>(null)

  const [trackedImport, setTrackedImport] =
    useState<TransactionImport | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [pollingError, setPollingError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    accountApi
      .getAccounts(0, 100)
      .then((response) => {
        if (!active) {
          return
        }

        const activeAccounts = response.content.filter(
          (account) => account.status === 'ACTIVE',
        )

        setAccounts(activeAccounts)
        setAccountId((currentAccountId) => {
          if (currentAccountId || activeAccounts.length === 0) {
            return currentAccountId
          }

          return String(activeAccounts[0].id)
        })
        setAccountsError(null)
      })
      .catch((error: unknown) => {
        if (active) {
          setAccountsError(
            getErrorMessage(error, 'Accounts could not be loaded.'),
          )
        }
      })
      .finally(() => {
        if (active) {
          setAccountsLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

  const trackedImportId = trackedImport?.id
  const trackedImportStatus = trackedImport?.status

  useEffect(() => {
    if (
      !trackedImportId ||
      !trackedImportStatus ||
      TERMINAL_STATUSES.has(trackedImportStatus)
    ) {
      return
    }

    let active = true

    const intervalId = window.setInterval(() => {
      transactionImportApi
        .getImport(trackedImportId)
        .then((response) => {
          if (active) {
            setTrackedImport(response)
            setPollingError(null)
          }
        })
        .catch((error: unknown) => {
          if (active) {
            setPollingError(
              getErrorMessage(
                error,
                'Automatic status refresh temporarily failed.',
              ),
            )
          }
        })
    }, 2500)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [trackedImportId, trackedImportStatus])

  const progressPercentage = useMemo(() => {
    if (!trackedImport || count(trackedImport.totalRows) === 0) {
      return 0
    }

    return Math.min(
      100,
      Math.round(
        (count(trackedImport.processedRows) /
          count(trackedImport.totalRows)) *
          100,
      ),
    )
  }, [trackedImport])

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setUploadError(null)
    setDownloadError(null)

    if (!accountId) {
      setUploadError('Select an account for this import.')

      return
    }

    if (!selectedFile) {
      setUploadError('Select a CSV file to upload.')

      return
    }

    if (!selectedFile.name.toLowerCase().endsWith('.csv')) {
      setUploadError('The selected file must use the .csv extension.')

      return
    }

    setUploading(true)

    try {
      const response = await transactionImportApi.submitImport(
        Number(accountId),
        selectedFile,
      )

      setTrackedImport(response)
      setLookupId(String(response.id))
      setSelectedFile(null)
      setPollingError(null)
    } catch (error) {
      setUploadError(
        getErrorMessage(error, 'The CSV file could not be submitted.'),
      )
    } finally {
      setUploading(false)
    }
  }

  async function handleLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLookupError(null)
    setDownloadError(null)

    const parsedImportId = Number(lookupId)

    if (!Number.isInteger(parsedImportId) || parsedImportId <= 0) {
      setLookupError('Enter a valid positive import ID.')

      return
    }

    setLookupLoading(true)

    try {
      const response =
        await transactionImportApi.getImport(parsedImportId)

      setTrackedImport(response)
      setPollingError(null)
    } catch (error) {
      setLookupError(
        getErrorMessage(error, 'The requested import could not be loaded.'),
      )
    } finally {
      setLookupLoading(false)
    }
  }

  async function refreshImport() {
    if (!trackedImport) {
      return
    }

    setRefreshing(true)
    setPollingError(null)

    try {
      const response = await transactionImportApi.getImport(
        trackedImport.id,
      )

      setTrackedImport(response)
    } catch (error) {
      setPollingError(
        getErrorMessage(error, 'The import status could not be refreshed.'),
      )
    } finally {
      setRefreshing(false)
    }
  }

  async function downloadRejectedOutput() {
    if (!trackedImport) {
      return
    }

    setDownloading(true)
    setDownloadError(null)

    try {
      const download =
        await transactionImportApi.getRejectedOutput(trackedImport.id)

      const downloadUrl = URL.createObjectURL(download.blob)
      const anchor = document.createElement('a')

      anchor.href = downloadUrl
      anchor.download = download.fileName
      document.body.append(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(downloadUrl)
    } catch (error) {
      setDownloadError(
        getErrorMessage(
          error,
          'The rejected-row CSV could not be downloaded.',
        ),
      )
    } finally {
      setDownloading(false)
    }
  }

  const statusDetails = trackedImport
    ? STATUS_DETAILS[trackedImport.status]
    : null
  const StatusIcon = statusDetails?.Icon

  return (
    <main className="page-content">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Batch processing</p>
          <h1>Transaction imports</h1>
          <p className="page-heading__description">
            Upload transaction CSV files and monitor their asynchronous
            processing status.
          </p>
        </div>
      </header>

      <section className="imports-layout">
        <article className="content-card import-upload-card">
          <div className="content-card__header">
            <div>
              <h2>Upload CSV</h2>
              <p>
                Select an active account and a CSV file up to 10 MB.
              </p>
            </div>

            <span className="import-card-icon">
              <UploadCloud size={21} />
            </span>
          </div>

          <form className="import-form" onSubmit={handleUpload}>
            {accountsError && (
              <div className="form-message form-message--error">
                {accountsError}
              </div>
            )}

            {uploadError && (
              <div className="form-message form-message--error">
                {uploadError}
              </div>
            )}

            <label className="form-field">
              Account
              <span className="form-control">
                <Landmark size={18} />

                <select
                  value={accountId}
                  onChange={(event) => setAccountId(event.target.value)}
                  disabled={accountsLoading || accounts.length === 0}
                >
                  {accountsLoading && (
                    <option value="">Loading accounts...</option>
                  )}

                  {!accountsLoading && accounts.length === 0 && (
                    <option value="">No active accounts available</option>
                  )}

                  {accounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name} · {account.currency}
                    </option>
                  ))}
                </select>
              </span>
            </label>

            <label className="import-file-picker">
              <input
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => {
                  setSelectedFile(event.target.files?.[0] ?? null)
                  setUploadError(null)
                }}
              />

              <span className="import-file-picker__icon">
                <FileSpreadsheet size={25} />
              </span>

              <span className="import-file-picker__copy">
                <strong>
                  {selectedFile
                    ? selectedFile.name
                    : 'Choose a CSV file'}
                </strong>

                <small>
                  {selectedFile
                    ? formatFileSize(selectedFile.size)
                    : 'Click to browse files on your device'}
                </small>
              </span>
            </label>

            <button
              className="button button--primary import-submit"
              type="submit"
              disabled={
                uploading ||
                accountsLoading ||
                accounts.length === 0
              }
            >
              {uploading ? (
                <LoaderCircle
                  className="import-icon--spinning"
                  size={18}
                />
              ) : (
                <UploadCloud size={18} />
              )}

              {uploading ? 'Submitting import...' : 'Submit import'}
            </button>
          </form>
        </article>

        <div className="imports-side-column">
          <article className="content-card import-lookup-card">
            <div className="content-card__header">
              <div>
                <h2>Track an import</h2>
                <p>Retrieve an existing import using its ID.</p>
              </div>

              <span className="import-card-icon">
                <Search size={20} />
              </span>
            </div>

            <form className="import-lookup-form" onSubmit={handleLookup}>
              {lookupError && (
                <div className="form-message form-message--error">
                  {lookupError}
                </div>
              )}

              <label className="form-field">
                Import ID
                <span className="form-control">
                  <Rows3 size={18} />

                  <input
                    type="number"
                    min="1"
                    step="1"
                    placeholder="For example, 42"
                    value={lookupId}
                    onChange={(event) => setLookupId(event.target.value)}
                  />
                </span>
              </label>

              <button
                className="button button--secondary"
                type="submit"
                disabled={lookupLoading}
              >
                {lookupLoading ? (
                  <LoaderCircle
                    className="import-icon--spinning"
                    size={17}
                  />
                ) : (
                  <Search size={17} />
                )}

                {lookupLoading ? 'Loading...' : 'Find import'}
              </button>
            </form>
          </article>

          <article className="content-card import-format-card">
            <div className="import-format-card__heading">
              <FileSpreadsheet size={20} />

              <div>
                <h2>Required CSV format</h2>
                <p>The header must match exactly.</p>
              </div>
            </div>

            <code className="import-format-card__code">
              transaction_date,transaction_type,amount,merchant,description
            </code>

            <p className="import-format-card__example">
              Example: 2026-08-20,EXPENSE,24.95,Coffee Shop,Morning coffee
            </p>
          </article>
        </div>
      </section>

      <section className="content-card import-status-card">
        {!trackedImport || !statusDetails || !StatusIcon ? (
          <div className="empty-state import-empty-state">
            <span className="empty-state__icon">
              <Rows3 size={27} />
            </span>

            <h3>No import selected</h3>
            <p>
              Submit a CSV or enter an import ID to monitor its status.
            </p>
          </div>
        ) : (
          <>
            <div className="import-status-card__header">
              <div className="import-status-card__identity">
                <span
                  className={`import-status-icon import-status-icon--${trackedImport.status.toLowerCase()}`}
                >
                  <StatusIcon
                    className={
                      trackedImport.status === 'RUNNING'
                        ? 'import-icon--spinning'
                        : undefined
                    }
                    size={22}
                  />
                </span>

                <div>
                  <div className="import-status-card__title">
                    <h2>{trackedImport.originalFileName}</h2>

                    <span
                      className={`import-status-badge import-status-badge--${trackedImport.status.toLowerCase()}`}
                    >
                      {statusDetails.label}
                    </span>
                  </div>

                  <p>
                    Import #{trackedImport.id} · {trackedImport.accountName}
                  </p>
                </div>
              </div>

              <div className="import-status-card__actions">
                {trackedImport.rejectedOutputAvailable && (
                  <button
                    className="button button--secondary"
                    type="button"
                    onClick={() => void downloadRejectedOutput()}
                    disabled={downloading}
                  >
                    {downloading ? (
                      <LoaderCircle
                        className="import-icon--spinning"
                        size={17}
                      />
                    ) : (
                      <Download size={17} />
                    )}

                    {downloading ? 'Downloading...' : 'Rejected CSV'}
                  </button>
                )}

                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => void refreshImport()}
                  disabled={refreshing}
                >
                  <RefreshCw
                    className={
                      refreshing ? 'import-icon--spinning' : undefined
                    }
                    size={17}
                  />

                  Refresh
                </button>
              </div>
            </div>

            <div className="import-status-summary">
              <div>
                <strong>{count(trackedImport.totalRows)}</strong>
                <span>Total rows</span>
              </div>

              <div>
                <strong>{count(trackedImport.processedRows)}</strong>
                <span>Processed</span>
              </div>

              <div>
                <strong>{count(trackedImport.successfulRows)}</strong>
                <span>Successful</span>
              </div>

              <div>
                <strong>{count(trackedImport.skippedRows)}</strong>
                <span>Skipped</span>
              </div>

              <div>
                <strong>{count(trackedImport.failedRows)}</strong>
                <span>Failed</span>
              </div>
            </div>

            <div className="import-progress-section">
              <div className="import-progress-section__heading">
                <div>
                  <strong>{statusDetails.description}</strong>

                  {!TERMINAL_STATUSES.has(trackedImport.status) && (
                    <span> Status refreshes automatically.</span>
                  )}
                </div>

                {count(trackedImport.totalRows) > 0 && (
                  <strong>{progressPercentage}%</strong>
                )}
              </div>

              <div
                className={`import-progress-track ${
                  trackedImport.status === 'RUNNING' &&
                  count(trackedImport.totalRows) === 0
                    ? 'import-progress-track--indeterminate'
                    : ''
                }`}
              >
                <span
                  style={{
                    width:
                      count(trackedImport.totalRows) > 0
                        ? `${progressPercentage}%`
                        : trackedImport.status === 'COMPLETED'
                          ? '100%'
                          : '0%',
                  }}
                />
              </div>
            </div>

            {(pollingError || downloadError) && (
              <div className="import-inline-message import-inline-message--error">
                <AlertTriangle size={17} />
                <span>{pollingError || downloadError}</span>
              </div>
            )}

            {trackedImport.failureSummary && (
              <div className="import-failure-summary">
                <AlertTriangle size={19} />

                <div>
                  <strong>Failure details</strong>
                  <p>{trackedImport.failureSummary}</p>
                </div>
              </div>
            )}

            <div className="import-metadata-grid">
              <div>
                <span>Account</span>
                <strong>{trackedImport.accountName}</strong>
              </div>

              <div>
                <span>File size</span>
                <strong>
                  {formatFileSize(trackedImport.fileSizeBytes)}
                </strong>
              </div>

              <div>
                <span>Submitted</span>
                <strong>{formatDateTime(trackedImport.createdAt)}</strong>
              </div>

              <div>
                <span>Started</span>
                <strong>{formatDateTime(trackedImport.startedAt)}</strong>
              </div>

              <div>
                <span>Completed</span>
                <strong>{formatDateTime(trackedImport.completedAt)}</strong>
              </div>

              <div>
                <span>Rejected output</span>
                <strong>
                  {trackedImport.rejectedOutputAvailable
                    ? 'Available'
                    : 'Not available'}
                </strong>
              </div>
            </div>
          </>
        )}
      </section>
    </main>
  )
}

export default ImportsPage