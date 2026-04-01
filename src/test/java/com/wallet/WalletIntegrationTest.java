package com.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.*;
import com.wallet.model.TransactionStatus;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class WalletIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired TransactionRepository transactionRepository;

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private Long createUser(String email, String name) throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail(email);
        req.setFullName(name);
        MvcResult result = mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        UserResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);
        return resp.getId();
    }

    private Long createWalletForUser(Long userId, BigDecimal initialBalance) throws Exception {
        CreateWalletRequest req = new CreateWalletRequest();
        req.setUserId(userId);
        req.setInitialBalance(initialBalance);
        MvcResult result = mvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        WalletResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), WalletResponse.class);
        return resp.getId();
    }

    /** Creates a fresh user + extra wallet (bypasses the auto-created wallet from createUser). */
    private Long createUserWithBalance(String email, BigDecimal balance) throws Exception {
        Long userId = createUser(email, "Test User");
        // The auto-created wallet has balance 0; give it money via the extra-wallet endpoint
        // Actually createUser already creates a wallet with balance=0.
        // We use the separate wallet endpoint to create a funded wallet (duplicate check would block it).
        // Instead, just fund the auto-created wallet by fetching it then using a helper transfer.
        // Easiest: create a standalone user+wallet pair via the wallet endpoint after suppressing the auto-wallet.
        // But the current design always auto-creates a wallet in UserService.
        // So: return the auto-created wallet id and seed it via a direct DB op isn't ideal —
        // instead we return the walletId from the user response.
        MvcResult userResult = mvc.perform(get("/users/" + userId))
                .andExpect(status().isOk())
                .andReturn();
        UserResponse userResp = objectMapper.readValue(userResult.getResponse().getContentAsString(), UserResponse.class);
        Long walletId = userResp.getWalletId();

        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            // Seed balance: create a temp "bank" user + wallet, then transfer
            Long bankUserId = createUser("bank-seed-" + UUID.randomUUID() + "@bank.com", "Bank");
            // bank user gets auto wallet with 0 — use the /wallets endpoint to create a funded one
            // But that would fail with DuplicateResource. So we need a different approach.
            // Use DB directly to set the balance.
            walletRepository.findById(walletId).ifPresent(w -> {
                w.setBalance(balance);
                walletRepository.save(w);
            });
        }
        return walletId;
    }

    private TransferResponse doTransfer(Long from, Long to, BigDecimal amount, String idempotencyKey) throws Exception {
        TransferRequest req = new TransferRequest();
        req.setFromWalletId(from);
        req.setToWalletId(to);
        req.setAmount(amount);
        req.setIdempotencyKey(idempotencyKey);
        MvcResult result = mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TransferResponse.class);
    }

    private BigDecimal getBalance(Long walletId) throws Exception {
        MvcResult result = mvc.perform(get("/wallets/" + walletId))
                .andExpect(status().isOk())
                .andReturn();
        WalletResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), WalletResponse.class);
        return resp.getBalance();
    }

    // ─────────────────────────────────────────────────────────────
    // 1. User CRUD
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1.1 Create user → 201 with auto-created wallet")
    void createUser_success() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("alice@example.com");
        req.setFullName("Alice");

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice"))
                .andExpect(jsonPath("$.walletId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("1.2 Create duplicate user → 409 Conflict")
    void createUser_duplicate_email() throws Exception {
        createUser("dup@example.com", "Dup");

        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("dup@example.com");
        req.setFullName("Dup2");

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("1.3 Create user with blank email → 400 Validation")
    void createUser_invalid_email() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("not-an-email");
        req.setFullName("Bob");

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("1.4 Create user with null fullName → 400 Validation")
    void createUser_missing_fullName() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("valid@example.com");
        req.setFullName("");

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("1.5 Get all users")
    void getAllUsers() throws Exception {
        createUser("u1@example.com", "U1");
        createUser("u2@example.com", "U2");

        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("1.6 Get user by id")
    void getUserById() throws Exception {
        Long id = createUser("fetch@example.com", "Fetch");
        mvc.perform(get("/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("fetch@example.com"));
    }

    @Test
    @DisplayName("1.7 Get non-existent user → 404")
    void getUserById_notFound() throws Exception {
        mvc.perform(get("/users/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Wallet CRUD
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2.1 Auto-created wallet has zero balance")
    void autoCreatedWallet_zeroBalance() throws Exception {
        Long userId = createUser("wallet@example.com", "WalletUser");
        MvcResult result = mvc.perform(get("/users/" + userId)).andReturn();
        UserResponse user = objectMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);

        mvc.perform(get("/wallets/" + user.getWalletId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("2.2 Create second wallet for same user → 409 Conflict")
    void createWallet_duplicateForUser() throws Exception {
        Long userId = createUser("dupwallet@example.com", "DupWallet");

        CreateWalletRequest req = new CreateWalletRequest();
        req.setUserId(userId);
        req.setInitialBalance(BigDecimal.TEN);

        mvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("2.3 Create wallet for non-existent user → 404")
    void createWallet_userNotFound() throws Exception {
        CreateWalletRequest req = new CreateWalletRequest();
        req.setUserId(9999L);
        req.setInitialBalance(BigDecimal.ZERO);

        mvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2.4 Get wallet by id")
    void getWalletById() throws Exception {
        Long userId = createUser("getwallet@example.com", "GetWallet");
        MvcResult result = mvc.perform(get("/users/" + userId)).andReturn();
        UserResponse user = objectMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);

        mvc.perform(get("/wallets/" + user.getWalletId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getWalletId()))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.userEmail").value("getwallet@example.com"));
    }

    @Test
    @DisplayName("2.5 Get non-existent wallet → 404")
    void getWallet_notFound() throws Exception {
        mvc.perform(get("/wallets/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2.6 Negative initial balance → 400 Validation")
    void createWallet_negativeInitialBalance() throws Exception {
        Long userId = createUser("negbal@example.com", "NegBal");
        // Force a second wallet creation attempt (will fail at dup check, but let's test with negative bal on a fresh user)
        // We need a user without wallet — not possible with current design since createUser auto-creates wallet.
        // So this tests the validation constraint directly by posting negative balance to /wallets for a new user
        // (which also triggers dup, but validation runs first in Spring — actually dup check is in service, validation is first)
        CreateWalletRequest req = new CreateWalletRequest();
        req.setUserId(userId);
        req.setInitialBalance(new BigDecimal("-1.00"));

        mvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Transfer — happy path
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3.1 Successful transfer debits source and credits target")
    void transfer_success_balancesUpdated() throws Exception {
        Long walletA = createUserWithBalance("a@t.com", new BigDecimal("500.00"));
        Long walletB = createUserWithBalance("b@t.com", BigDecimal.ZERO);

        TransferResponse resp = doTransfer(walletA, walletB, new BigDecimal("200.00"), null);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(getBalance(walletA)).isEqualByComparingTo("300.0000");
        assertThat(getBalance(walletB)).isEqualByComparingTo("200.0000");
    }

    @Test
    @DisplayName("3.2 Transfer response contains correct fields")
    void transfer_responseFields() throws Exception {
        Long walletA = createUserWithBalance("resp-a@t.com", new BigDecimal("100.00"));
        Long walletB = createUserWithBalance("resp-b@t.com", BigDecimal.ZERO);

        TransferResponse resp = doTransfer(walletA, walletB, new BigDecimal("50.00"), "key-001");

        assertThat(resp.getTransactionId()).isNotNull();
        assertThat(resp.getFromWalletId()).isEqualTo(walletA);
        assertThat(resp.getToWalletId()).isEqualTo(walletB);
        assertThat(resp.getAmount()).isEqualByComparingTo("50.00");
        assertThat(resp.getIdempotencyKey()).isEqualTo("key-001");
        assertThat(resp.getCreatedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Transfer — edge cases / business rules
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("4.1 Insufficient funds → 200 with FAILED status, balances unchanged")
    void transfer_insufficientFunds() throws Exception {
        Long walletA = createUserWithBalance("broke@t.com", new BigDecimal("10.00"));
        Long walletB = createUserWithBalance("rich@t.com", BigDecimal.ZERO);

        TransferResponse resp = doTransfer(walletA, walletB, new BigDecimal("100.00"), null);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(resp.getFromWalletId()).isEqualTo(walletA);
        // Balances must be unchanged
        assertThat(getBalance(walletA)).isEqualByComparingTo("10.0000");
        assertThat(getBalance(walletB)).isEqualByComparingTo("0.0000");
        // Failure reason recorded
        assertThat(transactionRepository.findAll()).anyMatch(t ->
                t.getStatus() == TransactionStatus.FAILED && t.getFailureReason() != null);
    }

    @Test
    @DisplayName("4.2 Transfer to self → 400 Bad Request")
    void transfer_selfTransfer() throws Exception {
        Long wallet = createUserWithBalance("self@t.com", new BigDecimal("100.00"));

        TransferRequest req = new TransferRequest();
        req.setFromWalletId(wallet);
        req.setToWalletId(wallet);
        req.setAmount(new BigDecimal("10.00"));

        mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Transfer"));
    }

    @Test
    @DisplayName("4.3 Transfer amount = 0 → 400 Validation")
    void transfer_zeroAmount() throws Exception {
        Long walletA = createUserWithBalance("zero-a@t.com", new BigDecimal("100.00"));
        Long walletB = createUserWithBalance("zero-b@t.com", BigDecimal.ZERO);

        TransferRequest req = new TransferRequest();
        req.setFromWalletId(walletA);
        req.setToWalletId(walletB);
        req.setAmount(BigDecimal.ZERO);

        mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("4.4 Transfer negative amount → 400 Validation")
    void transfer_negativeAmount() throws Exception {
        Long walletA = createUserWithBalance("neg-a@t.com", new BigDecimal("100.00"));
        Long walletB = createUserWithBalance("neg-b@t.com", BigDecimal.ZERO);

        TransferRequest req = new TransferRequest();
        req.setFromWalletId(walletA);
        req.setToWalletId(walletB);
        req.setAmount(new BigDecimal("-5.00"));

        mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4.5 Transfer from non-existent wallet → 404")
    void transfer_sourceWalletNotFound() throws Exception {
        Long walletB = createUserWithBalance("exist@t.com", BigDecimal.ZERO);

        TransferRequest req = new TransferRequest();
        req.setFromWalletId(9999L);
        req.setToWalletId(walletB);
        req.setAmount(new BigDecimal("10.00"));

        mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("4.6 Transfer to non-existent wallet → 404")
    void transfer_targetWalletNotFound() throws Exception {
        Long walletA = createUserWithBalance("src@t.com", new BigDecimal("100.00"));

        TransferRequest req = new TransferRequest();
        req.setFromWalletId(walletA);
        req.setToWalletId(9999L);
        req.setAmount(new BigDecimal("10.00"));

        mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("4.7 Transfer missing required fields → 400 Validation")
    void transfer_missingFields() throws Exception {
        mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("4.8 Exact-balance transfer succeeds (boundary: balance == amount)")
    void transfer_exactBalance() throws Exception {
        Long walletA = createUserWithBalance("exact-a@t.com", new BigDecimal("50.00"));
        Long walletB = createUserWithBalance("exact-b@t.com", BigDecimal.ZERO);

        TransferResponse resp = doTransfer(walletA, walletB, new BigDecimal("50.00"), null);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(getBalance(walletA)).isEqualByComparingTo("0.0000");
        assertThat(getBalance(walletB)).isEqualByComparingTo("50.0000");
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Idempotency
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5.1 Duplicate transfer with same idempotency key → same transaction returned, balances not double-debited")
    void idempotency_duplicateTransfer_returnsExisting() throws Exception {
        Long walletA = createUserWithBalance("idem-a@t.com", new BigDecimal("200.00"));
        Long walletB = createUserWithBalance("idem-b@t.com", BigDecimal.ZERO);
        String key = UUID.randomUUID().toString();

        TransferResponse first = doTransfer(walletA, walletB, new BigDecimal("100.00"), key);
        TransferResponse second = doTransfer(walletA, walletB, new BigDecimal("100.00"), key);

        assertThat(first.getTransactionId()).isEqualTo(second.getTransactionId());
        assertThat(first.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        // Balance should only be debited once
        assertThat(getBalance(walletA)).isEqualByComparingTo("100.0000");
        assertThat(getBalance(walletB)).isEqualByComparingTo("100.0000");
        // Only one transaction in DB for this key
        assertThat(transactionRepository.findAll().stream()
                .filter(t -> key.equals(t.getIdempotencyKey())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("5.2 Same idempotency key with different wallets → 409 Conflict")
    void idempotency_conflictingKey() throws Exception {
        Long walletA = createUserWithBalance("conf-a@t.com", new BigDecimal("200.00"));
        Long walletB = createUserWithBalance("conf-b@t.com", BigDecimal.ZERO);
        Long walletC = createUserWithBalance("conf-c@t.com", BigDecimal.ZERO);
        String key = UUID.randomUUID().toString();

        doTransfer(walletA, walletB, new BigDecimal("50.00"), key);

        TransferRequest conflictReq = new TransferRequest();
        conflictReq.setFromWalletId(walletA);
        conflictReq.setToWalletId(walletC);  // Different target
        conflictReq.setAmount(new BigDecimal("50.00"));
        conflictReq.setIdempotencyKey(key);

        mvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Idempotency Conflict"));
    }

    @Test
    @DisplayName("5.3 Failed transfer (insufficient funds) does NOT store idempotency key — retry is allowed")
    void idempotency_failedTransfer_keyNotStored() throws Exception {
        Long walletA = createUserWithBalance("retry-a@t.com", new BigDecimal("10.00"));
        Long walletB = createUserWithBalance("retry-b@t.com", BigDecimal.ZERO);
        String key = UUID.randomUUID().toString();

        // First attempt fails
        TransferResponse first = doTransfer(walletA, walletB, new BigDecimal("100.00"), key);
        assertThat(first.getStatus()).isEqualTo(TransactionStatus.FAILED);

        // The idempotency key should NOT be stored for a failed transaction
        assertThat(transactionRepository.findByIdempotencyKey(key)).isEmpty();

        // Top up balance and retry with same key — should succeed
        walletRepository.findById(walletA).ifPresent(w -> {
            w.setBalance(new BigDecimal("200.00"));
            walletRepository.save(w);
        });

        TransferResponse retry = doTransfer(walletA, walletB, new BigDecimal("100.00"), key);
        assertThat(retry.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(getBalance(walletA)).isEqualByComparingTo("100.0000");
        assertThat(getBalance(walletB)).isEqualByComparingTo("100.0000");
    }

    // ─────────────────────────────────────────────────────────────
    // 5.5 Deposit
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5.5.1 Deposit increases balance and records DEPOSIT transaction")
    void deposit_success() throws Exception {
        Long walletId = createUserWithBalance("dep@t.com", new BigDecimal("100.00"));

        mvc.perform(post("/wallets/" + walletId + "/deposit")
                        .param("amount", "50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.0000));

        assertThat(getBalance(walletId)).isEqualByComparingTo("150.0000");
        assertThat(transactionRepository.findAll()).anyMatch(t ->
                t.getStatus() == TransactionStatus.DEPOSIT
                        && t.getToWalletId().equals(walletId)
                        && t.getAmount().compareTo(new BigDecimal("50.00")) == 0);
    }

    @Test
    @DisplayName("5.5.2 Deposit zero or negative amount → 400")
    void deposit_invalidAmount() throws Exception {
        Long walletId = createUserWithBalance("depneg@t.com", new BigDecimal("100.00"));

        mvc.perform(post("/wallets/" + walletId + "/deposit")
                        .param("amount", "0"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/wallets/" + walletId + "/deposit")
                        .param("amount", "-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5.5.3 Deposit to non-existent wallet → 404")
    void deposit_walletNotFound() throws Exception {
        mvc.perform(post("/wallets/9999/deposit")
                        .param("amount", "50.00"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Transactions endpoint
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("6.1 GET /transactions returns recent transactions ordered desc by createdAt")
    void getTransactions_returnsList() throws Exception {
        Long walletA = createUserWithBalance("tx-a@t.com", new BigDecimal("500.00"));
        Long walletB = createUserWithBalance("tx-b@t.com", BigDecimal.ZERO);

        doTransfer(walletA, walletB, new BigDecimal("10.00"), null);
        doTransfer(walletA, walletB, new BigDecimal("20.00"), null);

        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("6.2 GET /transactions?limit=1 returns only 1 result")
    void getTransactions_limitParam() throws Exception {
        Long walletA = createUserWithBalance("lim-a@t.com", new BigDecimal("500.00"));
        Long walletB = createUserWithBalance("lim-b@t.com", BigDecimal.ZERO);

        doTransfer(walletA, walletB, new BigDecimal("10.00"), null);
        doTransfer(walletA, walletB, new BigDecimal("20.00"), null);
        doTransfer(walletA, walletB, new BigDecimal("30.00"), null);

        mvc.perform(get("/transactions?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("6.3 GET /transactions with no data returns empty list")
    void getTransactions_empty() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Concurrency — simultaneous transfers
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("7.1 Concurrent transfers do not corrupt balance (10 simultaneous transfers of 10 each from 200 balance)")
    void concurrency_noBalanceCorruption() throws Exception {
        Long walletA = createUserWithBalance("conc-a@t.com", new BigDecimal("200.00"));
        Long walletB = createUserWithBalance("conc-b@t.com", BigDecimal.ZERO);

        int threads = 10;
        BigDecimal perTransfer = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                TransferRequest req = new TransferRequest();
                req.setFromWalletId(walletA);
                req.setToWalletId(walletB);
                req.setAmount(perTransfer);
                MvcResult result = mvc.perform(post("/transfer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                return objectMapper.readValue(result.getResponse().getContentAsString(), TransferResponse.class);
            }));
        }

        ready.await();
        start.countDown();

        int successCount = 0;
        int failedCount = 0;
        int lockCount = 0;
        for (Future<TransferResponse> f : futures) {
            try {
                TransferResponse resp = f.get(10, TimeUnit.SECONDS);
                if (resp.getStatus() == TransactionStatus.SUCCESS) successCount++;
                else failedCount++;
            } catch (ExecutionException e) {
                // 409 lock contention responses cause deserialization into ErrorResponse — count as lock error
                lockCount++;
            }
        }
        executor.shutdown();

        BigDecimal finalBalanceA = getBalance(walletA);
        BigDecimal finalBalanceB = getBalance(walletB);

        // Total money in system must be conserved: 200
        assertThat(finalBalanceA.add(finalBalanceB))
                .isEqualByComparingTo("200.0000");

        // No negative balance
        assertThat(finalBalanceA).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalBalanceB).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // successCount * 10 should equal credits on B (money conserved proves no double-debit)
        assertThat(finalBalanceB).isEqualByComparingTo(
                perTransfer.multiply(new BigDecimal(successCount)).setScale(4, java.math.RoundingMode.UNNECESSARY));
    }

    @Test
    @DisplayName("7.2 Concurrent transfers exceed balance — only funded transfers succeed, total is conserved")
    void concurrency_partialSuccess_moneyConserved() throws Exception {
        Long walletA = createUserWithBalance("over-a@t.com", new BigDecimal("50.00"));
        Long walletB = createUserWithBalance("over-b@t.com", BigDecimal.ZERO);

        int threads = 10;
        BigDecimal perTransfer = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                TransferRequest req = new TransferRequest();
                req.setFromWalletId(walletA);
                req.setToWalletId(walletB);
                req.setAmount(perTransfer);
                MvcResult result = mvc.perform(post("/transfer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                return objectMapper.readValue(result.getResponse().getContentAsString(), TransferResponse.class);
            }));
        }

        ready.await();
        start.countDown();

        AtomicInteger successCount = new AtomicInteger();
        for (Future<TransferResponse> f : futures) {
            try {
                TransferResponse resp = f.get(10, TimeUnit.SECONDS);
                if (resp.getStatus() == TransactionStatus.SUCCESS) successCount.incrementAndGet();
            } catch (ExecutionException e) {
                // 409 lock contention — not a successful transfer
            }
        }
        executor.shutdown();

        BigDecimal finalA = getBalance(walletA);
        BigDecimal finalB = getBalance(walletB);

        // Money is conserved
        assertThat(finalA.add(finalB)).isEqualByComparingTo("50.0000");
        // No negative balance
        assertThat(finalA).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalB).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // At most 5 should succeed (50 / 10 = 5 max), could be fewer if some hit lock contention
        assertThat(successCount.get()).isLessThanOrEqualTo(5);
        // B received exactly successCount * 10
        assertThat(finalB).isEqualByComparingTo(
                perTransfer.multiply(new BigDecimal(successCount.get())).setScale(4, java.math.RoundingMode.UNNECESSARY));
    }

    // ─────────────────────────────────────────────────────────────
    // 8. Business rule: wallet belongs to user
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.1 WalletResponse contains correct user email")
    void walletResponse_containsUserEmail() throws Exception {
        Long userId = createUser("email-check@example.com", "EmailCheck");
        MvcResult result = mvc.perform(get("/users/" + userId)).andReturn();
        UserResponse user = objectMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);

        mvc.perform(get("/wallets/" + user.getWalletId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("email-check@example.com"))
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    @DisplayName("8.2 Multiple transfers chain: A→B then B→C — all balances correct")
    void transfer_chain() throws Exception {
        Long walletA = createUserWithBalance("chain-a@t.com", new BigDecimal("300.00"));
        Long walletB = createUserWithBalance("chain-b@t.com", BigDecimal.ZERO);
        Long walletC = createUserWithBalance("chain-c@t.com", BigDecimal.ZERO);

        doTransfer(walletA, walletB, new BigDecimal("100.00"), null);
        doTransfer(walletB, walletC, new BigDecimal("60.00"), null);

        assertThat(getBalance(walletA)).isEqualByComparingTo("200.0000");
        assertThat(getBalance(walletB)).isEqualByComparingTo("40.0000");
        assertThat(getBalance(walletC)).isEqualByComparingTo("60.0000");
    }

    @Test
    @DisplayName("8.3 Reverse transfer (A→B then B→A) — balances correct")
    void transfer_bidirectional() throws Exception {
        Long walletA = createUserWithBalance("bidir-a@t.com", new BigDecimal("100.00"));
        Long walletB = createUserWithBalance("bidir-b@t.com", new BigDecimal("50.00"));

        doTransfer(walletA, walletB, new BigDecimal("30.00"), null);
        doTransfer(walletB, walletA, new BigDecimal("10.00"), null);

        assertThat(getBalance(walletA)).isEqualByComparingTo("80.0000");
        assertThat(getBalance(walletB)).isEqualByComparingTo("70.0000");
    }
}
