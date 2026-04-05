# Digital Wallet — Java 21 Backend

A production-style digital wallet application with a **Java 21 / Spring Boot 3.3** backend, **PostgreSQL** database, and **React** frontend. Full Docker Compose setup included.

## Architecture

| Component | Technology | Port |
|-----------|------------|------|
| **Backend** | Java 21, Spring Boot 3.3, Spring Data JPA | `8080` |
| **Database** | PostgreSQL 16 | `5432` |
| **Frontend** | React 18 + Vite (Nginx) | `3000` |

## Features

- **User Management** — Create users, each auto-assigned a wallet
- **Wallet Operations** — View balances, deposit funds
- **Money Transfers** — Transfer between wallets with pessimistic locking (deadlock-safe ordered locking)
- **Idempotency** — Transfer requests support idempotency keys
- **Optimistic Concurrency** — Version-based concurrency control on wallets
- **Transaction History** — Full audit trail of deposits, transfers, and failures
- **Global Error Handling** — Consistent error responses via exception handler

## Quick Start (Docker Compose)

```bash
docker compose up --build
```

Once running:
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **PostgreSQL**: `localhost:5432` (user: `wallet_user`, pass: `wallet_pass`)

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/users` | Create a new user (auto-creates wallet) |
| `GET` | `/users` | List all users |
| `GET` | `/users/{id}` | Get user by ID |
| `POST` | `/wallets` | Create a new wallet |
| `GET` | `/wallets/{id}` | Get wallet by ID |
| `POST` | `/wallets/{id}/deposit?amount=100` | Deposit funds |
| `POST` | `/transfer` | Transfer between wallets |
| `GET` | `/transactions?limit=20` | Recent transactions |

## Project Structure

```
digital-wallet/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── src/
│   └── main/java/com/wallet/
│       ├── config/              # App configuration
│       ├── controller/          # API endpoints
│       ├── dto/                 # Request/Response models
│       ├── exception/           # Custom exceptions
│       ├── model/               # JPA entity models
│       ├── repository/          # Spring Data repositories
│       └── service/             # Business logic
└── frontend/
    ├── src/                     # React components & API layer
    ├── nginx.conf               # Production proxy config
    └── Dockerfile
```

## Local Development (without Docker)

### Backend
```bash
mvn spring-boot:run
```
Requires a PostgreSQL instance at `localhost:5432` with database `digital_wallet`.

### Frontend
```bash
cd frontend
npm install
npm run dev
```
Dev server runs at `http://localhost:3000` with API proxy to `http://localhost:8080`.
