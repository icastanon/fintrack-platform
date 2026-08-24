import { createContext } from 'react'
import type {
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserProfile,
} from '../types/auth'

export interface AuthContextValue {
  user: UserProfile | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (request: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  updateProfile: (request: UpdateProfileRequest) => Promise<UserProfile>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)