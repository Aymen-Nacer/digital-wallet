# Digital Wallet

A production-style Digital Wallet system built with Java 17, Spring Boot 3, PostgreSQL, and a React frontend.

---

## Frontend

A clean React dashboard for demo / portfolio purposes built with Vite + React + Axios.

### Pages

| Page | Route | Description |
|---|---|---|
| Wallets | default | All users and their live wallet balances |
| Transfer | Transfer tab | Send money between wallets |
| History | History tab | Last 20 transactions with status |

### Run frontend locally (dev mode)

```bash
cd frontend
npm install
npm run dev
# Opens on http://localhost:3000 — proxies /api → http://localhost:8080
```

### Run full stack with Docker Compose

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| **Frontend** | http://localhost:3000 |
| **Backend API** | http://localhost:8080 |
| **PostgreSQL** | localhost:5432 |

### Connect frontend to a different backend URL

Set the environment variable before building:

```bash
VITE_API_URL=http://your-backend-host:8080 npm run build
```

---

---

## Tech Stack

- **Java 17** + **Spring Boot 3.2**
- **PostgreSQL 16**
- **Spring Data JPA / Hibernate**
- **Docker + Docker Compose**
- **Lombok**

---

## Architecture

```
src/main/java/com/wallet/
├── controller/         → REST API layer
│   ├── UserController.java
│   ├── WalletController.java
│   └── TransferController.java
├── service/            → Business logic
│   ├── UserService.java
│   ├── WalletService.java
│   └── TransferService.java
├── repository/         → Data access (Spring Data JPA)
│   ├── UserRepository.java
│   ├── WalletRepository.java
│   └── TransactionRepository.java
├── model/              → JPA entities
│   ├── User.java
│   ├── Wallet.java
│   ├── Transaction.java
│   └── TransactionStatus.java
├── dto/                → Request / Response objects
│   ├── CreateUserRequest.java
│   ├── UserResponse.java
│   ├── CreateWalletRequest.java
│   ├── WalletResponse.java
│   ├── TransferRequest.java
│   ├── TransferResponse.java
│   └── ErrorResponse.java
└── exception/          → Custom exceptions + global handler
    ├── GlobalExceptionHandler.java
    ├── ResourceNotFoundException.java
    ├── InsufficientBalanceException.java
    ├── DuplicateResourceException.java
    ├── InvalidTransferException.java
    └── IdempotencyConflictException.java
```

---

## Key Design Decisions

### Money Transfer — ACID + Race Condition Prevention

The transfer flow in `TransferService` guarantees correctness under concurrency:

1. **Pessimistic Locking** — `SELECT FOR UPDATE` is issued on both wallets via `@Lock(PESSIMISTIC_WRITE)`. No other transaction can read-and-modify these rows until the lock is released.
2. **Deadlock Prevention** — Wallets are always locked in **ascending ID order**, so two concurrent transfers between wallet A and wallet B always acquire locks in the same sequence (lower ID first), eliminating circular waits.
3. **Optimistic Locking** — `@Version` on `Wallet` acts as a secondary guard against stale updates.
4. **Isolation Level** — `REPEATABLE_READ` prevents phantom reads within the transaction.
5. **Atomic Ledger** — A `Transaction` record is written in the **same database transaction** as the balance update. Rollback of either means both roll back together.
6. **Failed Transfers Are Logged** — Insufficient balance still records a `FAILED` transaction entry.

### Idempotency

Pass an `idempotencyKey` in the transfer request. If the same key is received again, the original result is returned without re-executing the transfer.

---

## How to Run

### Option 1 — Docker Compose (Recommended)

```bash
docker compose up --build
```

The app starts on `http://localhost:8080`. PostgreSQL is on port `5432`.

### Option 2 — Run Locally (PostgreSQL must be running)

1. Start PostgreSQL and create the database:
   ```sql
   CREATE DATABASE digital_wallet;
   CREATE USER wallet_user WITH PASSWORD 'wallet_pass';
   GRANT ALL PRIVILEGES ON DATABASE digital_wallet TO wallet_user;
   ```

2. Build and run:
   ```bash
   mvn clean package -DskipTests
   java -jar target/digital-wallet-1.0.0.jar
   ```

---

## API Reference & Sample Requests

### POST /users — Create a user
> A wallet is automatically created for every new user with a balance of 0.

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "fullName": "Alice Smith"
  }'
```

**Response (201 Created):**
```json
{
  "id": 1,
  "email": "alice@example.com",
  "fullName": "Alice Smith",
  "walletId": 1,
  "createdAt": "2024-03-28T10:00:00"
}
```

---

### POST /wallets — Create an additional wallet (optional)
> Useful only if you want a wallet with a non-zero initial balance; normally `POST /users` already creates a wallet.

```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "initialBalance": 500.00
  }'
```

**Response (201 Created):**
```json
{
  "id": 2,
  "userId": 2,
  "userEmail": "bob@example.com",
  "balance": 500.0000,
  "createdAt": "2024-03-28T10:01:00",
  "updatedAt": "2024-03-28T10:01:00"
}
```

---

### GET /wallets/{id} — Get wallet balance

```bash
curl http://localhost:8080/wallets/1
```

**Response (200 OK):**
```json
{
  "id": 1,
  "userId": 1,
  "userEmail": "alice@example.com",
  "balance": 250.0000,
  "createdAt": "2024-03-28T10:00:00",
  "updatedAt": "2024-03-28T10:05:00"
}
```

---

### POST /transfer — Transfer money

```bash
curl -X POST http://localhost:8080/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "fromWalletId": 1,
    "toWalletId": 2,
    "amount": 100.00,
    "idempotencyKey": "txn-unique-key-001"
  }'
```

**Response — SUCCESS (200 OK):**
```json
{
  "transactionId": 1,
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 100.0000,
  "status": "SUCCESS",
  "idempotencyKey": "txn-unique-key-001",
  "createdAt": "2024-03-28T10:05:00"
}
```

**Response — FAILED (insufficient balance, 200 OK, logged):**
```json
{
  "transactionId": 2,
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 999999.00,
  "status": "FAILED",
  "idempotencyKey": null,
  "createdAt": "2024-03-28T10:06:00"
}
```

---

## Error Responses

All errors follow a consistent structure:

```json
{
  "status": 422,
  "error": "Insufficient Balance",
  "message": "Insufficient balance: available=50.0000, requested=100.0000",
  "details": null,
  "timestamp": "2024-03-28T10:07:00"
}
```

| HTTP Status | Scenario |
|---|---|
| 400 | Invalid input / same-wallet transfer |
| 404 | User or wallet not found |
| 409 | Duplicate email / wallet / idempotency conflict |
| 422 | Insufficient balance |
| 500 | Unexpected server error |

---

## Complete End-to-End Flow (curl)

```bash
# 1. Create user Alice
curl -s -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","fullName":"Alice Smith"}' | jq .

# 2. Create user Bob
curl -s -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@example.com","fullName":"Bob Jones"}' | jq .

# 3. Top up Alice's wallet (wallet id=1)
curl -s -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"initialBalance":1000.00}' | jq .

# 4. Transfer 250 from Alice (wallet 1) to Bob (wallet 2)
curl -s -X POST http://localhost:8080/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromWalletId":1,"toWalletId":2,"amount":250.00,"idempotencyKey":"pay-001"}' | jq .

# 5. Check balances
curl -s http://localhost:8080/wallets/1 | jq .balance
curl -s http://localhost:8080/wallets/2 | jq .balance
```
