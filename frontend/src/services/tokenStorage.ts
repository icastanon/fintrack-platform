import type { AuthResponse } from '../types/auth'

const STORAGE_KEY = 'fintrack-auth'

function getTokens(): AuthResponse | null {
  try {
    const storedValue = sessionStorage.getItem(STORAGE_KEY)

    return storedValue ? (JSON.parse(storedValue) as AuthResponse) : null
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)

    return null
  }
}

function saveTokens(tokens: AuthResponse) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tokens))
}

function clearTokens() {
  sessionStorage.removeItem(STORAGE_KEY)
}

export const tokenStorage = {
  getTokens,
  saveTokens,
  clearTokens,
  getAccessToken: () => getTokens()?.accessToken ?? null,
  getRefreshToken: () => getTokens()?.refreshToken ?? null,
  hasTokens: () => getTokens() !== null,
}