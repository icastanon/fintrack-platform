import { useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowRight, LockKeyhole, UserRound } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../services/apiClient'

interface LoginLocationState {
  from?: string
  message?: string
}

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as LoginLocationState | null

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage('')
    setSubmitting(true)

    try {
      await login({ username, password })
      navigate(locationState?.from ?? '/', { replace: true })
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError
          ? error.message
          : 'FinTrack could not complete the login request.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-card">
      <header className="auth-card__header">
        <p className="eyebrow">Welcome back</p>
        <h1>Sign in to FinTrack</h1>
        <p>Enter your username and password to continue.</p>
      </header>

      {locationState?.message && (
        <div className="form-message form-message--success">
          {locationState.message}
        </div>
      )}

      {errorMessage && (
        <div className="form-message form-message--error">
          {errorMessage}
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit}>
        <label className="form-field">
          <span>Username</span>
          <span className="form-control">
            <UserRound size={18} />
            <input
              type="text"
              name="username"
              autoComplete="username"
              value={username}
              required
              onChange={(event) => setUsername(event.target.value)}
            />
          </span>
        </label>

        <label className="form-field">
          <span>Password</span>
          <span className="form-control">
            <LockKeyhole size={18} />
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              required
              onChange={(event) => setPassword(event.target.value)}
            />
          </span>
        </label>

        <button
          className="button button--primary auth-submit"
          type="submit"
          disabled={submitting}
        >
          {submitting ? 'Signing in...' : 'Sign in'}
          {!submitting && <ArrowRight size={18} />}
        </button>
      </form>

      <p className="auth-card__switch">
        Don’t have an account? <Link to="/register">Create one</Link>
      </p>
    </div>
  )
}

export default LoginPage