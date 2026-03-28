import { useState } from 'react'
import { postTransfer } from '../api/transfer'
import styles from './TransferForm.module.css'

export default function TransferForm({ onSuccess }) {
  const [form, setForm] = useState({
    fromWalletId: '',
    toWalletId: '',
    amount: '',
  })
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

    const payload = {
      fromWalletId: Number(form.fromWalletId),
      toWalletId: Number(form.toWalletId),
      amount: parseFloat(form.amount),
      idempotencyKey: `txn-${Date.now()}`,
    }

    try {
      const { data } = await postTransfer(payload)

      if (data.status === 'SUCCESS') {
        setResult({ type: 'success', message: `Transfer successful! Transaction #${data.transactionId}` })
        setForm({ fromWalletId: '', toWalletId: '', amount: '' })
        onSuccess?.()
      } else {
        setResult({ type: 'failed', message: `Transfer failed: insufficient balance.` })
        onSuccess?.()
      }
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        'Transfer failed. Please check the wallet IDs and try again.'
      setResult({ type: 'error', message: msg })
    } finally {
      setLoading(false)
    }
  }

  const isValid =
    form.fromWalletId && form.toWalletId && form.amount && parseFloat(form.amount) > 0

  return (
    <div className={styles.wrapper}>
      <form onSubmit={handleSubmit} className={styles.form}>
        <div className={styles.row}>
          <Field
            label="From Wallet ID"
            name="fromWalletId"
            value={form.fromWalletId}
            onChange={handleChange}
            placeholder="e.g. 1"
            type="number"
            min="1"
          />
          <Field
            label="To Wallet ID"
            name="toWalletId"
            value={form.toWalletId}
            onChange={handleChange}
            placeholder="e.g. 2"
            type="number"
            min="1"
          />
          <Field
            label="Amount"
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
                <SendIcon /> Send Transfer
              </>
            )}
          </button>
        </div>
      </form>

      {result && (
        <div className={`${styles.alert} ${styles[result.type]}`}>
          {result.type === 'success' && <CheckIcon />}
          {result.type === 'failed' && <WarningIcon />}
          {result.type === 'error' && <ErrorIcon />}
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

function SendIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="22" y1="2" x2="11" y2="13" />
      <polygon points="22 2 15 22 11 13 2 9 22 2" />
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

function WarningIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
      <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
    </svg>
  )
}

function ErrorIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
    </svg>
  )
}
