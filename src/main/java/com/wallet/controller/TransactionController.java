package com.wallet.controller;

import com.wallet.dto.TransferResponse;
import com.wallet.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<TransferResponse>> getRecentTransactions(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(transactionService.getRecentTransactions(limit));
    }
}
