import {
  ChartNoAxesCombined,
  ShieldCheck,
  WalletCards,
  Workflow,
} from 'lucide-react'
import { Link, Outlet } from 'react-router-dom'

function AuthLayout() {
  return (
    <div className="auth-page">
      <section className="auth-panel">
        <Link className="auth-panel__brand" to="/">
          <span>
            <ChartNoAxesCombined size={24} />
          </span>
          FinTrack
        </Link>

        <div className="auth-panel__content">
          <p className="eyebrow auth-panel__eyebrow">
            Personal finance, organized
          </p>
          <h1>Understand where your money is going.</h1>
          <p>
            Track accounts, process transactions, monitor monthly budgets,
            and receive useful spending alerts.
          </p>

          <ul className="auth-benefits">
            <li>
              <WalletCards size={20} />
              <span>Keep account balances and transactions together.</span>
            </li>
            <li>
              <Workflow size={20} />
              <span>Process manual entries and large CSV imports.</span>
            </li>
            <li>
              <ShieldCheck size={20} />
              <span>Secure access with short-lived authentication tokens.</span>
            </li>
          </ul>
        </div>

        <p className="auth-panel__footer">
          A portfolio-scale financial tracking platform.
        </p>
      </section>

      <section className="auth-form-area">
        <Link className="auth-mobile-brand" to="/">
          <ChartNoAxesCombined size={23} />
          FinTrack
        </Link>

        <Outlet />
      </section>
    </div>
  )
}

export default AuthLayout