import { useCallback, useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import type {
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserProfile,
} from '../types/auth'
import * as authApi from '../services/authApi'
import { tokenStorage } from '../services/tokenStorage'
import { AuthContext } from './AuthContext'

function AuthProvider({ children }: PropsWithChildren) {
  const [shouldRestoreSession] = useState(() => tokenStorage.hasTokens())
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(shouldRestoreSession)

  useEffect(() => {
    if (!shouldRestoreSession) {
      return
    }

    let active = true

    const restoreSession = async () => {
      try {
        const currentUser = await authApi.getCurrentUser()

        if (active) {
          setUser(currentUser)
        }
      } catch {
        tokenStorage.clearTokens()
      } finally {
        if (active) {
          setIsLoading(false)
        }
      }
    }

    void restoreSession()

    return () => {
      active = false
    }
  }, [shouldRestoreSession])

  const login = useCallback(async (request: LoginRequest) => {
    const tokens = await authApi.login(request)
    tokenStorage.saveTokens(tokens)

    try {
      const currentUser = await authApi.getCurrentUser()
      setUser(currentUser)
    } catch (error) {
      tokenStorage.clearTokens()
      throw error
    }
  }, [])

  const register = useCallback(async (request: RegisterRequest) => {
    await authApi.register(request)
  }, [])

  const updateProfile = useCallback(
    async (request: UpdateProfileRequest) => {
      const updatedUser = await authApi.updateCurrentUser(request)
      setUser(updatedUser)

      return updatedUser
    },
    [],
  )

  const logout = useCallback(async () => {
    const refreshToken = tokenStorage.getRefreshToken()

    try {
      if (refreshToken) {
        await authApi.logout(refreshToken)
      }
    } finally {
      tokenStorage.clearTokens()
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      login,
      register,
      updateProfile,
      logout,
    }),
    [
      user,
      isLoading,
      login,
      register,
      updateProfile,
      logout,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider