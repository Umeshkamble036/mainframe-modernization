package com.custacct.dto;

import com.custacct.entity.Transaction;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO for submitting a new transaction via REST API.
 * Replaces feeding records into DAILY.TRANSACTIONS.RAW.dat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRequest {

    @NotNull(message = "Transaction type is required (DP/WD/TR/PM)")
    private Transaction.TransactionType type;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    private LocalDate date;

    private LocalTime time;

    @Size(max = 16)
    private String reference;

    @Size(max = 40)
    private String description;

    private Transaction.TransactionChannel channel;
}
