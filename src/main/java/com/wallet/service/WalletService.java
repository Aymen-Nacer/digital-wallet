package com.wallet.service;

import java.math.BigDecimal;

import com.wallet.dto.CreateWalletRequest;
import com.wallet.dto.WalletResponse;
import com.wallet.exception.DuplicateResourceException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.model.Transaction;
import com.wallet.model.TransactionStatus;
import com.wallet.model.User;
import com.wallet.model.Wallet;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

        if (walletRepository.existsByUserId(request.getUserId())) {
            throw new DuplicateResourceException("Wallet already exists for user id: " + request.getUserId());
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(request.getInitialBalance())
                .build();

        wallet = walletRepository.save(wallet);
        log.info("Created wallet id={} for user id={} with balance={}", wallet.getId(), user.getId(), wallet.getBalance());

        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletById(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId));
        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse deposit(Long walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId));

        wallet.setBalance(wallet.getBalance().add(amount));
        wallet = walletRepository.save(wallet);

        transactionRepository.save(Transaction.builder()
                .fromWalletId(null)
                .toWalletId(walletId)
                .amount(amount)
                .status(TransactionStatus.DEPOSIT)
                .build());

        log.info("Deposited {} to wallet id={}, new balance={}", amount, walletId, wallet.getBalance());

        return toResponse(wallet);
    }

    public WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .userEmail(wallet.getUser().getEmail())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
