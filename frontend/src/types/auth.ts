export type SupportedCurrency = 'USD' | 'EUR' | 'GBP' | 'CAD' | 'AUD'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  currency: SupportedCurrency
}

export interface UpdateProfileRequest {
  email: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessTokenExpiresIn: number
  refreshTokenExpiresAt: string
}

export interface UserProfile {
  id: number
  username: string
  email: string
  currency: SupportedCurrency
  role: 'USER' | 'ADMIN'
  createdAt: string
}

export interface ErrorResponse {
  status: number
  message: string
  timestamp: string
  errors: Record<string, string>
}