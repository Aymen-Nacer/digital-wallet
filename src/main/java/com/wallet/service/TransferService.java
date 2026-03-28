package com.wallet.service;

import com.wallet.dto.TransferRequest;
import com.wallet.dto.TransferResponse;
import com.wallet.exception.IdempotencyConflictException;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.InvalidTransferException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.model.Transaction;
import com.wallet.model.TransactionStatus;
import com.wallet.model.Wallet;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Transfers money between two wallets atomically.
     *
     * Race condition prevention strategy:
     *   - Pessimistic locking (SELECT FOR UPDATE) on both wallets.
     *   - Wallets are always locked in ascending ID order to prevent deadlocks.
     *   - Optimistic locking (@Version) as a secondary guard.
     *   - REPEATABLE_READ isolation ensures consistent reads within the transaction.
     *
     * Idempotency:
     *   - If an idempotencyKey is provided and already exists, return the existing result.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransferResponse transfer(TransferRequest request) {
        validateRequest(request);

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            return handleWithIdempotency(request);
        }

        return executeTransfer(request);
    }

    private TransferResponse handleWithIdempotency(TransferRequest request) {
        return transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .map(existing -> {
                    if (!existing.getFromWalletId().equals(request.getFromWalletId())
                            || !existing.getToWalletId().equals(request.getToWalletId())
                            || existing.getAmount().compareTo(request.getAmount()) != 0) {
                        throw new IdempotencyConflictException(
                                "Idempotency key '" + request.getIdempotencyKey()
                                        + "' was already used for a different transfer");
                    }
                    log.info("Idempotency hit for key={}, returning existing transaction id={}",
                            request.getIdempotencyKey(), existing.getId());
                    return toResponse(existing);
                })
                .orElseGet(() -> executeTransfer(request));
    }

    private TransferResponse executeTransfer(TransferRequest request) {
        log.info("Starting transfer: fromWallet={} toWallet={} amount={}",
                request.getFromWalletId(), request.getToWalletId(), request.getAmount());

        // Lock wallets in consistent order (ascending ID) to prevent deadlocks
        Long firstLockId = Math.min(request.getFromWalletId(), request.getToWalletId());
        Long secondLockId = Math.max(request.getFromWalletId(), request.getToWalletId());

        Wallet firstLocked = walletRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + firstLockId));
        Wallet secondLocked = walletRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + secondLockId));

        Wallet sourceWallet = firstLockId.equals(request.getFromWalletId()) ? firstLocked : secondLocked;
        Wallet targetWallet = firstLockId.equals(request.getToWalletId()) ? firstLocked : secondLocked;

        // Validate sufficient balance
        if (sourceWallet.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient balance in wallet id={}: available={}, requested={}",
                    sourceWallet.getId(), sourceWallet.getBalance(), request.getAmount());

            Transaction failed = recordFailedTransaction(request,
                    "Insufficient balance: available=" + sourceWallet.getBalance()
                            + ", requested=" + request.getAmount());
            return toResponse(failed);
        }

        // Debit source
        sourceWallet.setBalance(sourceWallet.getBalance().subtract(request.getAmount()));
        // Credit target
        targetWallet.setBalance(targetWallet.getBalance().add(request.getAmount()));

        walletRepository.save(sourceWallet);
        walletRepository.save(targetWallet);

        Transaction success = recordTransaction(request, TransactionStatus.SUCCESS, null);

        log.info("Transfer completed: transactionId={} fromWallet={} toWallet={} amount={}",
                success.getId(), request.getFromWalletId(), request.getToWalletId(), request.getAmount());

        return toResponse(success);
    }

    private void validateRequest(TransferRequest request) {
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new InvalidTransferException("Cannot transfer to the same wallet");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be positive");
        }
    }

    private Transaction recordTransaction(TransferRequest request, TransactionStatus status, String failureReason) {
        Transaction transaction = Transaction.builder()
                .fromWalletId(request.getFromWalletId())
                .toWalletId(request.getToWalletId())
                .amount(request.getAmount())
                .status(status)
                .idempotencyKey(request.getIdempotencyKey())
                .failureReason(failureReason)
                .build();
        return transactionRepository.save(transaction);
    }

    /**
     * Records a FAILED transaction without storing the idempotency key,
     * so that retries with the same key can re-attempt the transfer.
     */
    private Transaction recordFailedTransaction(TransferRequest request, String failureReason) {
        Transaction transaction = Transaction.builder()
                .fromWalletId(request.getFromWalletId())
                .toWalletId(request.getToWalletId())
                .amount(request.getAmount())
                .status(TransactionStatus.FAILED)
                .idempotencyKey(null)
                .failureReason(failureReason)
                .build();
        return transactionRepository.save(transaction);
    }

    private TransferResponse toResponse(Transaction transaction) {
        return TransferResponse.builder()
                .transactionId(transaction.getId())
                .fromWalletId(transaction.getFromWalletId())
                .toWalletId(transaction.getToWalletId())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .idempotencyKey(transaction.getIdempotencyKey())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
