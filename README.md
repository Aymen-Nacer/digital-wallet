# Digital Wallet

A production-ready digital wallet system featuring atomic transfers, concurrency control, and a modern dashboard.

## Quick Start (Docker)

The easiest way to run the full stack (Backend, Frontend, and PostgreSQL):

```bash
docker compose up --build
```

- **Frontend**: [http://localhost:3000](http://localhost:3000)
- **Backend API**: [http://localhost:8080](http://localhost:8080)
- **Database**: `localhost:5432`

---

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.3, Spring Data JPA
- **Frontend**: React 18, Vite, Axios
- **Database**: PostgreSQL 16
- **Infrastructure**: Docker, Docker Compose

---

## Key Features & Design

### Atomic Transfers
- **Pessimistic Locking**: Uses `SELECT FOR UPDATE` to prevent race conditions during balance updates.
- **Deadlock Prevention**: Always locks wallets in ascending ID order.
- **ACID Compliance**: Transactions and ledger entries are committed atomically.
- **Idempotency**: Supports `idempotencyKey` to prevent duplicate processing of the same request.

### API Highlights

| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `/users` | `POST` | Create user & automatic wallet |
| `/wallets/{id}` | `GET` | Retrieve balance & details |
| `/wallets/{id}/deposit` | `POST` | Deposit funds into a wallet |
| `/transfer` | `POST` | Atomic money transfer between wallets |
| `/transactions` | `GET` | List recent transaction history |

---

## Local Development

### Backend (Java 21)
1. Ensure PostgreSQL is running.
2. Configure `application.properties` or set env vars for DB connection.
3. Run:
   ```bash
   mvn spring-boot:run
   ```

### Frontend (Node.js)
```bash
cd frontend
npm install
npm run dev
```

---
