import { useState } from 'react'
import WalletList from './components/WalletList'
import TransferForm from './components/TransferForm'
import DepositForm from './components/DepositForm'
import TransactionHistory from './components/TransactionHistory'
import CreateUserModal from './components/CreateUserModal'
import styles from './App.module.css'

const TABS = [
  { id: 'wallets', label: 'Wallets', icon: WalletIcon },
  { id: 'deposit', label: 'Deposit', icon: DepositIcon },
  { id: 'transfer', label: 'Transfer', icon: TransferIcon },
  { id: 'history', label: 'History', icon: HistoryIcon },
]

export default function App() {
  const [activeTab, setActiveTab] = useState('wallets')
  const [showCreateUser, setShowCreateUser] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)

  const triggerRefresh = () => setRefreshKey((k) => k + 1)

  const handleTransferSuccess = () => {
    triggerRefresh()
  }

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <div className={styles.brand}>
            <div className={styles.logo}>
              <LogoIcon />
            </div>
            <div>
              <span className={styles.brandName}>Digital Wallet</span>
              <span className={styles.brandSub}>Management System</span>
            </div>
          </div>

          <nav className={styles.nav}>
            {TABS.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                className={`${styles.navBtn} ${activeTab === id ? styles.navBtnActive : ''}`}
                onClick={() => setActiveTab(id)}
              >
                <Icon />
                <span>{label}</span>
              </button>
            ))}
          </nav>

          <button
            className={styles.createBtn}
            onClick={() => setShowCreateUser(true)}
          >
            <PlusIcon />
            New User
          </button>
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.container}>
          {activeTab === 'wallets' && (
            <Section
              title="User Wallets"
              subtitle="Live balances for all registered wallets"
              action={
                <button
                  className={styles.actionBtn}
                  onClick={() => setShowCreateUser(true)}
                >
                  <PlusIcon /> Add User
                </button>
              }
            >
              <WalletList refreshTrigger={refreshKey} />
            </Section>
          )}

          {activeTab === 'deposit' && (
            <Section
              title="Deposit Funds"
              subtitle="Add funds to any wallet instantly"
            >
              <DepositForm onSuccess={triggerRefresh} />
            </Section>
          )}

          {activeTab === 'transfer' && (
            <Section
              title="Money Transfer"
              subtitle="Send funds between wallets instantly"
            >
              <TransferForm onSuccess={handleTransferSuccess} />
            </Section>
          )}

          {activeTab === 'history' && (
            <Section
              title="Transaction History"
              subtitle="Latest 20 transactions across all wallets"
            >
              <TransactionHistory refreshTrigger={refreshKey} />
            </Section>
          )}
        </div>
      </main>

      <footer className={styles.footer}>
        <span>Digital Wallet · Java Spring Boot + React · Demo</span>
      </footer>

      {showCreateUser && (
        <CreateUserModal
          onClose={() => setShowCreateUser(false)}
          onCreated={triggerRefresh}
        />
      )}
    </div>
  )
}

function Section({ title, subtitle, action, children }) {
  return (
    <div className={styles.section}>
      <div className={styles.sectionHeader}>
        <div>
          <h1 className={styles.sectionTitle}>{title}</h1>
          {subtitle && <p className={styles.sectionSubtitle}>{subtitle}</p>}
        </div>
        {action && <div>{action}</div>}
      </div>
      {children}
    </div>
  )
}

function LogoIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="5" width="20" height="14" rx="2" />
      <line x1="2" y1="10" x2="22" y2="10" />
    </svg>
  )
}

function WalletIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 12V22H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h14" />
      <path d="M20 12a2 2 0 0 0-2-2H4" />
      <path d="M20 2v4" />
      <circle cx="17" cy="12" r="1" />
    </svg>
  )
}

function TransferIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="17 1 21 5 17 9" />
      <path d="M3 11V9a4 4 0 0 1 4-4h14" />
      <polyline points="7 23 3 19 7 15" />
      <path d="M21 13v2a4 4 0 0 1-4 4H3" />
    </svg>
  )
}

function HistoryIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="12 8 12 12 14 14" />
      <path d="M3.05 11a9 9 0 1 0 .5-4.5" />
      <polyline points="3 3 3 9 9 9" />
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

function PlusIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  )
}
