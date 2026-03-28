import { useState, useEffect, useCallback } from 'react'
import { getAllUsers } from '../api/users'
import { getWallet } from '../api/wallets'
import styles from './WalletList.module.css'

export default function WalletList({ refreshTrigger }) {
  const [wallets, setWallets] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchWallets = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data: users } = await getAllUsers()
      const walletDetails = await Promise.all(
        users
          .filter((u) => u.walletId)
          .map((u) =>
            getWallet(u.walletId).then((res) => ({
              ...res.data,
              fullName: u.fullName,
            }))
          )
      )
      setWallets(walletDetails)
    } catch (err) {
      setError('Failed to load wallets. Is the backend running?')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchWallets()
  }, [fetchWallets, refreshTrigger])

  if (loading) return <div className={styles.state}>Loading wallets…</div>
  if (error) return <div className={`${styles.state} ${styles.error}`}>{error}</div>
  if (wallets.length === 0)
    return (
      <div className={styles.state}>
        No wallets found. Create a user to get started.
      </div>
    )

  return (
    <div className={styles.wrapper}>
      <div className={styles.header}>
        <span className={styles.count}>{wallets.length} wallet{wallets.length !== 1 ? 's' : ''}</span>
        <button className={styles.refresh} onClick={fetchWallets} title="Refresh">
          <RefreshIcon />
        </button>
      </div>
      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Wallet ID</th>
              <th>User</th>
              <th>Email</th>
              <th className={styles.right}>Balance</th>
              <th>Last Updated</th>
            </tr>
          </thead>
          <tbody>
            {wallets.map((w) => (
              <tr key={w.id}>
                <td>
                  <span className={styles.id}>#{w.id}</span>
                </td>
                <td className={styles.name}>{w.fullName}</td>
                <td className={styles.email}>{w.userEmail}</td>
                <td className={`${styles.right} ${styles.balance}`}>
                  ${Number(w.balance).toFixed(2)}
                </td>
                <td className={styles.date}>{formatDate(w.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
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
    hour: '2-digit', minute: '2-digit',
  })
}
