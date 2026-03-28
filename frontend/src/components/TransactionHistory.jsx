import { useState, useEffect, useCallback } from 'react'
import { getTransactions } from '../api/transactions'
import styles from './TransactionHistory.module.css'

export default function TransactionHistory({ refreshTrigger }) {
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchTransactions = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await getTransactions(20)
      setTransactions(data)
    } catch (err) {
      setError('Failed to load transactions.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchTransactions()
  }, [fetchTransactions, refreshTrigger])

  if (loading) return <div className={styles.state}>Loading transactions…</div>
  if (error) return <div className={`${styles.state} ${styles.errorState}`}>{error}</div>
  if (transactions.length === 0)
    return (
      <div className={styles.state}>No transactions yet. Make a deposit or transfer to see history.</div>
    )

  return (
    <div className={styles.wrapper}>
      <div className={styles.header}>
        <span className={styles.count}>{transactions.length} recent transaction{transactions.length !== 1 ? 's' : ''}</span>
        <button className={styles.refresh} onClick={fetchTransactions} title="Refresh">
          <RefreshIcon />
        </button>
      </div>
      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>ID</th>
              <th>From Wallet</th>
              <th>To Wallet</th>
              <th className={styles.right}>Amount</th>
              <th>Status</th>
              <th>Timestamp</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((tx) => (
              <tr key={tx.transactionId}>
                <td>
                  <span className={styles.id}>#{tx.transactionId}</span>
                </td>
                <td>
                  {tx.fromWalletId != null
                    ? <span className={styles.walletBadge}>Wallet {tx.fromWalletId}</span>
                    : <span className={styles.depositSource}>External</span>}
                </td>
                <td>
                  <span className={styles.walletBadge}>Wallet {tx.toWalletId}</span>
                </td>
                <td className={`${styles.right} ${styles.amount}`}>
                  ${Number(tx.amount).toFixed(2)}
                </td>
                <td>
                  <StatusBadge status={tx.status} />
                </td>
                <td className={styles.date}>{formatDate(tx.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function StatusBadge({ status }) {
  const cls =
    status === 'SUCCESS' ? styles.badgeSuccess
    : status === 'DEPOSIT' ? styles.badgeDeposit
    : styles.badgeFailed
  return <span className={`${styles.badge} ${cls}`}>{status}</span>
}

function RefreshIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="23 4 23 10 17 10" />
      <polyline points="1 20 1 14 7 14" />
      <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
    </svg>
  )
}

function formatDate(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString(undefined, {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}
