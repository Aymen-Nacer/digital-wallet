import { useState } from 'react'
import { depositToWallet } from '../api/wallets'
import styles from './DepositForm.module.css'

export default function DepositForm({ onSuccess }) {
  const [form, setForm] = useState({ walletId: '', amount: '' })
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))
    setResult(null)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setResult(null)
    setLoading(true)

    try {
      const { data } = await depositToWallet(Number(form.walletId), parseFloat(form.amount))
      setResult({
        type: 'success',
        message: `Deposited $${parseFloat(form.amount).toFixed(2)} to Wallet #${data.id}. New balance: $${Number(data.balance).toFixed(2)}`,
      })
      setForm({ walletId: '', amount: '' })
      onSuccess?.()
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        'Deposit failed. Please check the wallet ID and try again.'
      setResult({ type: 'error', message: msg })
    } finally {
      setLoading(false)
    }
  }

  const isValid = form.walletId && form.amount && parseFloat(form.amount) > 0

  return (
    <div className={styles.wrapper}>
      <div className={styles.infoBox}>
        <InfoIcon />
        <span>Add funds directly to any wallet. The updated balance will reflect immediately across Wallets and Transaction History.</span>
      </div>

      <form onSubmit={handleSubmit} className={styles.form}>
        <div className={styles.row}>
          <Field
            label="Wallet ID"
            name="walletId"
            value={form.walletId}
            onChange={handleChange}
            placeholder="e.g. 1"
            type="number"
            min="1"
          />
          <Field
            label="Deposit Amount"
            name="amount"
            value={form.amount}
            onChange={handleChange}
            placeholder="0.00"
            type="number"
            min="0.01"
            step="0.01"
          />
        </div>

        <div className={styles.actions}>
          <button
            type="submit"
            className={styles.submit}
            disabled={!isValid || loading}
          >
            {loading ? (
              <>
                <Spinner /> Processing…
              </>
            ) : (
              <>
                <DepositIcon /> Add Funds
              </>
            )}
          </button>
        </div>
      </form>

      {result && (
        <div className={`${styles.alert} ${styles[result.type]}`}>
          {result.type === 'success' ? <CheckIcon /> : <ErrorIcon />}
          <span>{result.message}</span>
        </div>
      )}
    </div>
  )
}

function Field({ label, name, value, onChange, placeholder, type, min, step }) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={name}>{label}</label>
      <input
        id={name}
        name={name}
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        min={min}
        step={step}
        className={styles.input}
        required
      />
    </div>
  )
}

function Spinner() {
  return (
    <svg className={styles.spin} width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
      <path d="M21 12a9 9 0 1 1-6.219-8.56" />
    </svg>
  )
}

function DepositIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="16" />
      <line x1="8" y1="12" x2="16" y2="12" />
    </svg>
  )
}

function InfoIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="16" x2="12" y2="12" />
      <line x1="12" y1="8" x2="12.01" y2="8" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  )
}

function ErrorIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  )
}
