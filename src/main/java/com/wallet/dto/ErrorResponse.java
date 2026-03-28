package com.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private List<String> details;
    private LocalDateTime timestamp;
}
