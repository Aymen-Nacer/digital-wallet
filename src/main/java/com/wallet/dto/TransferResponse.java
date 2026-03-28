package com.wallet.dto;

import com.wallet.model.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransferResponse {

    private Long transactionId;
    private Long fromWalletId;
    private Long toWalletId;
    private BigDecimal amount;
    private TransactionStatus status;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
