import { useState } from 'react'
import {
  CalendarDays,
  CheckCircle2,
  Mail,
  ShieldCheck,
  UserRound,
  WalletCards,
} from 'lucide-react'
import type { FormEvent } from 'react'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../services/apiClient'

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'long',
  }).format(new Date(value))
}

function ProfilePage() {
  const { user, updateProfile } = useAuth()
  const [email, setEmail] = useState(user?.email ?? '')
  const [isSaving, setIsSaving] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSuccessMessage('')
    setErrorMessage('')
    setIsSaving(true)

    try {
      await updateProfile({ email: email.trim() })
      setSuccessMessage('Your profile email was updated successfully.')
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(
          error.fieldErrors.email ||
            error.message ||
            'Your profile could not be updated.',
        )
      } else {
        setErrorMessage('Your profile could not be updated.')
      }
    } finally {
      setIsSaving(false)
    }
  }

  if (!user) {
    return null
  }

  return (
    <>
      <header className="page-heading">
        <div>
          <p className="eyebrow">Account settings</p>
          <h1>Your profile</h1>
          <p className="page-heading__description">
            Review your FinTrack identity and update your contact email.
          </p>
        </div>
      </header>

      <div className="profile-layout">
        <section className="profile-card profile-card--identity">
          <div className="profile-avatar">
            {user.username.slice(0, 2).toUpperCase()}
          </div>

          <div>
            <p className="profile-card__label">FinTrack member</p>
            <h2>{user.username}</h2>
            <p>{user.email}</p>
          </div>

          <span className="profile-role">
            <ShieldCheck size={16} />
            {user.role}
          </span>
        </section>

        <section className="profile-card">
          <div className="profile-card__header">
            <div>
              <p className="profile-card__label">Profile details</p>
              <h2>Personal information</h2>
            </div>

            <UserRound size={23} />
          </div>

          {successMessage && (
            <div className="form-message form-message--success">
              <CheckCircle2 size={17} />
              <span>{successMessage}</span>
            </div>
          )}

          {errorMessage && (
            <div className="form-message form-message--error">
              {errorMessage}
            </div>
          )}

          <form className="profile-form" onSubmit={handleSubmit}>
            <label className="form-field">
              Username
              <span className="form-control profile-control--read-only">
                <UserRound size={18} />
                <input value={user.username} disabled />
              </span>
              <small>Usernames cannot be changed.</small>
            </label>

            <label className="form-field">
              Email address
              <span className="form-control">
                <Mail size={18} />
                <input
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(event) => {
                    setEmail(event.target.value)
                    setSuccessMessage('')
                    setErrorMessage('')
                  }}
                />
              </span>
            </label>

            <button
              className="button button--primary profile-submit"
              type="submit"
              disabled={
                isSaving ||
                !email.trim() ||
                email.trim() === user.email
              }
            >
              {isSaving ? 'Saving…' : 'Save email'}
            </button>
          </form>
        </section>

        <section className="profile-card">
          <div className="profile-card__header">
            <div>
              <p className="profile-card__label">Account details</p>
              <h2>FinTrack configuration</h2>
            </div>

            <WalletCards size={23} />
          </div>

          <dl className="profile-details">
            <div>
              <dt>
                <WalletCards size={17} />
                Base currency
              </dt>
              <dd>{user.currency}</dd>
              <small>
                Your base currency is selected during registration and
                cannot be changed.
              </small>
            </div>

            <div>
              <dt>
                <ShieldCheck size={17} />
                Access role
              </dt>
              <dd>{user.role}</dd>
            </div>

            <div>
              <dt>
                <CalendarDays size={17} />
                Member since
              </dt>
              <dd>{formatDate(user.createdAt)}</dd>
            </div>
          </dl>
        </section>
      </div>
    </>
  )
}

export default ProfilePage