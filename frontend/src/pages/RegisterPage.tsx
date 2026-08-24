import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowRight,
  Coins,
  LockKeyhole,
  Mail,
  UserRound,
} from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../services/apiClient'
import type { SupportedCurrency } from '../types/auth'

const supportedCurrencies: SupportedCurrency[] = [
  'USD',
  'EUR',
  'GBP',
  'CAD',
  'AUD',
]

function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [currency, setCurrency] = useState<SupportedCurrency>('USD')
  const [password, setPassword] = useState('')
  const [confirmedPassword, setConfirmedPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage('')
    setFieldErrors({})

    if (password !== confirmedPassword) {
      setFieldErrors({
        confirmedPassword: 'Passwords must match',
      })

      return
    }

    setSubmitting(true)

    try {
      await register({
        username,
        email,
        password,
        currency,
      })

      navigate('/login', {
        replace: true,
        state: {
          message: 'Your FinTrack account was created. You can now sign in.',
        },
      })
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message)
        setFieldErrors(error.fieldErrors)
      } else {
        setErrorMessage('FinTrack could not create the account.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-card auth-card--register">
      <header className="auth-card__header">
        <p className="eyebrow">Get started</p>
        <h1>Create your account</h1>
        <p>Set up your FinTrack profile and base currency.</p>
      </header>

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
          {fieldErrors.username && (
            <small className="field-error">{fieldErrors.username}</small>
          )}
        </label>

        <label className="form-field">
          <span>Email</span>
          <span className="form-control">
            <Mail size={18} />
            <input
              type="email"
              name="email"
              autoComplete="email"
              value={email}
              required
              onChange={(event) => setEmail(event.target.value)}
            />
          </span>
          {fieldErrors.email && (
            <small className="field-error">{fieldErrors.email}</small>
          )}
        </label>

        <label className="form-field">
          <span>Base currency</span>
          <span className="form-control">
            <Coins size={18} />
            <select
              name="currency"
              value={currency}
              required
              onChange={(event) =>
                setCurrency(event.target.value as SupportedCurrency)
              }
            >
              {supportedCurrencies.map((supportedCurrency) => (
                <option key={supportedCurrency} value={supportedCurrency}>
                  {supportedCurrency}
                </option>
              ))}
            </select>
          </span>
          {fieldErrors.currency && (
            <small className="field-error">{fieldErrors.currency}</small>
          )}
        </label>

        <label className="form-field">
          <span>Password</span>
          <span className="form-control">
            <LockKeyhole size={18} />
            <input
              type="password"
              name="password"
              autoComplete="new-password"
              minLength={10}
              maxLength={64}
              value={password}
              required
              onChange={(event) => setPassword(event.target.value)}
            />
          </span>
          <small className="field-hint">Use between 10 and 64 characters.</small>
          {fieldErrors.password && (
            <small className="field-error">{fieldErrors.password}</small>
          )}
        </label>

        <label className="form-field">
          <span>Confirm password</span>
          <span className="form-control">
            <LockKeyhole size={18} />
            <input
              type="password"
              name="confirmedPassword"
              autoComplete="new-password"
              minLength={10}
              maxLength={64}
              value={confirmedPassword}
              required
              onChange={(event) =>
                setConfirmedPassword(event.target.value)
              }
            />
          </span>
          {fieldErrors.confirmedPassword && (
            <small className="field-error">
              {fieldErrors.confirmedPassword}
            </small>
          )}
        </label>

        <button
          className="button button--primary auth-submit"
          type="submit"
          disabled={submitting}
        >
          {submitting ? 'Creating account...' : 'Create account'}
          {!submitting && <ArrowRight size={18} />}
        </button>
      </form>

      <p className="auth-card__switch">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </div>
  )
}

export default RegisterPage