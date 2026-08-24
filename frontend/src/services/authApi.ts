import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserProfile,
} from '../types/auth'
import { apiRequest } from './apiClient'

export function login(request: LoginRequest) {
  return apiRequest<AuthResponse>(
    '/api/v1/auth/login',
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
    false,
  )
}

export function register(request: RegisterRequest) {
  return apiRequest<void>(
    '/api/v1/auth/register',
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
    false,
  )
}

export function getCurrentUser() {
  return apiRequest<UserProfile>('/api/v1/users/me')
}

export function updateCurrentUser(request: UpdateProfileRequest) {
  return apiRequest<UserProfile>('/api/v1/users/me', {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function logout(refreshToken: string) {
  return apiRequest<void>(
    '/api/v1/auth/logout',
    {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    },
    false,
  )
}