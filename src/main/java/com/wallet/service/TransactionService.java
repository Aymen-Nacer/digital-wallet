package com.wallet.service;

import com.wallet.dto.TransferResponse;
import com.wallet.model.Transaction;
import com.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<TransferResponse> getRecentTransactions(int limit) {
        return transactionRepository
                .findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private TransferResponse toResponse(Transaction t) {
        return TransferResponse.builder()
                .transactionId(t.getId())
                .fromWalletId(t.getFromWalletId())
                .toWalletId(t.getToWalletId())
                .amount(t.getAmount())
                .status(t.getStatus())
                .idempotencyKey(t.getIdempotencyKey())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
