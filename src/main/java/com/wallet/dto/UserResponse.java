package com.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String fullName;
    private Long walletId;
    private LocalDateTime createdAt;
}
