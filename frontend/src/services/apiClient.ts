import type { AuthResponse, ErrorResponse } from '../types/auth'
import { tokenStorage } from './tokenStorage'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(
    status: number,
    message: string,
    fieldErrors: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export interface ApiDownload {
  blob: Blob
  fileName: string
}

function buildUrl(path: string) {
  return `${apiBaseUrl}${path}`
}

async function createApiError(response: Response) {
  try {
    const errorResponse = (await response.json()) as ErrorResponse

    return new ApiError(
      response.status,
      errorResponse.message || 'The request could not be completed.',
      errorResponse.errors,
    )
  } catch {
    return new ApiError(
      response.status,
      'The request could not be completed.',
    )
  }
}

let refreshPromise: Promise<boolean> | null = null

async function performTokenRefresh(): Promise<boolean> {
  const refreshToken = tokenStorage.getRefreshToken()

  if (!refreshToken) {
    return false
  }

  try {
    const response = await fetch(buildUrl('/api/v1/auth/refresh'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    })

    if (!response.ok) {
      tokenStorage.clearTokens()

      return false
    }

    const tokens = (await response.json()) as AuthResponse
    tokenStorage.saveTokens(tokens)

    return true
  } catch {
    tokenStorage.clearTokens()

    return false
  }
}

async function refreshTokens() {
  if (!refreshPromise) {
    refreshPromise = performTokenRefresh().finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

function createAuthenticatedHeaders(headers?: HeadersInit) {
  const authenticatedHeaders = new Headers(headers)
  const accessToken = tokenStorage.getAccessToken()

  if (accessToken) {
    authenticatedHeaders.set('Authorization', `Bearer ${accessToken}`)
  }

  return authenticatedHeaders
}

function getDownloadFileName(response: Response, fallbackFileName: string) {
  const contentDisposition = response.headers.get('Content-Disposition')

  if (!contentDisposition) {
    return fallbackFileName
  }

  const encodedMatch = contentDisposition.match(
    /filename\*=UTF-8''([^;]+)/i,
  )

  if (encodedMatch?.[1]) {
    try {
      return decodeURIComponent(encodedMatch[1])
    } catch {
      return encodedMatch[1]
    }
  }

  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)

  return plainMatch?.[1] || fallbackFileName
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  authenticated = true,
  allowRefresh = true,
): Promise<T> {
  const headers = new Headers(options.headers)

  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  if (authenticated) {
    const accessToken = tokenStorage.getAccessToken()

    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }
  }

  const response = await fetch(buildUrl(path), {
    ...options,
    headers,
  })

  if (
    response.status === 401 &&
    authenticated &&
    allowRefresh &&
    (await refreshTokens())
  ) {
    return apiRequest<T>(path, options, authenticated, false)
  }

  if (!response.ok) {
    throw await createApiError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const responseText = await response.text()

  if (!responseText) {
    return undefined as T
  }

  return JSON.parse(responseText) as T
}

export async function apiDownload(
  path: string,
  fallbackFileName: string,
  allowRefresh = true,
): Promise<ApiDownload> {
  const response = await fetch(buildUrl(path), {
    headers: createAuthenticatedHeaders(),
  })

  if (
    response.status === 401 &&
    allowRefresh &&
    (await refreshTokens())
  ) {
    return apiDownload(path, fallbackFileName, false)
  }

  if (!response.ok) {
    throw await createApiError(response)
  }

  return {
    blob: await response.blob(),
    fileName: getDownloadFileName(response, fallbackFileName),
  }
}