package com.custacct;

import com.custacct.entity.Customer;
import com.custacct.entity.Transaction;
import com.custacct.repository.CustomerRepository;
import com.custacct.repository.TransactionRepository;
import com.custacct.service.TransactionProcessingService;
import com.custacct.service.TransactionProcessingService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit/Integration tests for TransactionProcessingService.
 *
 * These tests verify that the Java business logic faithfully mirrors
 * the COBOL 2200-VALIDATE-TRANSACTION and 2300-APPLY-TRANSACTION paragraphs.
 */
@SpringBootTest
@Transactional
class TransactionProcessingServiceTest {

    @Autowired
    private TransactionProcessingService service;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Customer activeCustomer;
    private Customer inactiveCustomer;

    @BeforeEach
    void setUp() {
        activeCustomer = customerRepository.save(Customer.builder()
                .id(9001L)
                .lastName("TEST").firstName("ACTIVE")
                .accountBalance(new BigDecimal("1000.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .minBalance(new BigDecimal("100.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.now())
                .transactionCount(0).branchCode(1001)
                .build());

        inactiveCustomer = customerRepository.save(Customer.builder()
                .id(9002L)
                .lastName("TEST").firstName("INACTIVE")
                .accountBalance(new BigDecimal("500.00"))
                .creditLimit(new BigDecimal("1000.00"))
                .minBalance(BigDecimal.ZERO)
                .status(Customer.CustomerStatus.INACTIVE)
                .openDate(LocalDate.now())
                .transactionCount(0).branchCode(1001)
                .build());
    }

    // ----------------------------------------------------------------
    // 2200-VALIDATE-TRANSACTION tests
    // ----------------------------------------------------------------

    @Test
    @DisplayName("WHEN TXN-AMOUNT <= ZEROS → validation fails")
    void validateTransaction_negativeAmount_fails() {
        Transaction txn = buildTxn(9001L, Transaction.TransactionType.DP, "-10.00");
        ValidationResult result = service.validateTransaction(activeCustomer, txn);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("POSITIVE");
    }

    @Test
    @DisplayName("WHEN NOT CUST-ACTIVE → validation fails")
    void validateTransaction_inactiveAccount_fails() {
        Transaction txn = buildTxn(9002L, Transaction.TransactionType.DP, "100.00");
        ValidationResult result = service.validateTransaction(inactiveCustomer, txn);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("ACTIVE");
    }

    @Test
    @DisplayName("WHEN TXN-WITHDRAWAL AND TXN-AMOUNT > CUST-ACCOUNT-BAL → insufficient funds")
    void validateTransaction_insufficientFunds_fails() {
        Transaction txn = buildTxn(9001L, Transaction.TransactionType.WD, "5000.00");
        ValidationResult result = service.validateTransaction(activeCustomer, txn);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("INSUFFICIENT");
    }

    @Test
    @DisplayName("WHEN withdrawal would breach minimum balance → validation fails")
    void validateTransaction_breachesMinBalance_fails() {
        // Balance 1000, min 100, withdrawal 950 → leaves 50 which is < 100
        Transaction txn = buildTxn(9001L, Transaction.TransactionType.WD, "950.00");
        ValidationResult result = service.validateTransaction(activeCustomer, txn);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("MINIMUM BALANCE");
    }

    @Test
    @DisplayName("Valid deposit on active account passes validation")
    void validateTransaction_validDeposit_passes() {
        Transaction txn = buildTxn(9001L, Transaction.TransactionType.DP, "500.00");
        ValidationResult result = service.validateTransaction(activeCustomer, txn);
        assertThat(result.valid()).isTrue();
    }

    // ----------------------------------------------------------------
    // 2300-APPLY-TRANSACTION tests
    // ----------------------------------------------------------------

    @Test
    @DisplayName("DEPOSIT: ADD TXN-AMOUNT TO CUST-ACCOUNT-BAL")
    void applyTransaction_deposit_addsToBalance() {
        Transaction txn = buildTxn(9001L, Transaction.TransactionType.DP, "500.00");
        BigDecimal originalBalance = activeCustomer.getAccountBalance();

        ApplyResult result = service.applyTransaction(activeCustomer, txn);

        assertThat(result.success()).isTrue();
        assertThat(activeCustomer.getAccountBalance())
                .isEqualByComparingTo(originalBalance.add(new BigDecimal("500.00")));
    }

    @Test
    @DisplayName("WITHDRAWAL: SUBTRACT TXN-AMOUNT FROM CUST-ACCOUNT-BAL")
    void applyTransaction_withdrawal_subtractsFromBalance() {
        Transaction txn = buildTxn(9001L, Transaction.TransactionType.WD, "200.00");
        BigDecimal originalBalance = activeCustomer.getAccountBalance();

        ApplyResult result = service.applyTransaction(activeCustomer, txn);

        assertThat(result.success()).isTrue();
        assertThat(activeCustomer.getAccountBalance())
                .isEqualByComparingTo(originalBalance.subtract(new BigDecimal("200.00")));
    }

    @Test
    @DisplayName("PAYMENT: SUBTRACT, floor at zero (COBOL: IF CUST-ACCOUNT-BAL < ZEROS → ZEROS)")
    void applyTransaction_payment_floorsAtZero() {
        // Payment larger than balance — COBOL floors at 0, not negative
        Transaction txn = buildTxn(9001L, Transaction.TransactionType.PM, "5000.00");
        ApplyResult result = service.applyTransaction(activeCustomer, txn);

        assertThat(result.success()).isTrue();
        assertThat(activeCustomer.getAccountBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ----------------------------------------------------------------
    // Full processTransaction integration tests
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Full flow: active customer deposit succeeds and increments count")
    void processTransaction_fullDeposit_success() {
        Transaction txn = transactionRepository.save(buildTxn(9001L, Transaction.TransactionType.DP, "250.00"));
        int initialCount = activeCustomer.getTransactionCount();

        ProcessResult result = service.processTransaction(txn);

        assertThat(result.success()).isTrue();
        assertThat(result.newBalance()).isEqualByComparingTo(new BigDecimal("1250.00"));

        Customer updated = customerRepository.findById(9001L).orElseThrow();
        assertThat(updated.getTransactionCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("Full flow: inactive customer transaction fails with correct error")
    void processTransaction_inactiveCustomer_fails() {
        Transaction txn = transactionRepository.save(buildTxn(9002L, Transaction.TransactionType.DP, "100.00"));

        ProcessResult result = service.processTransaction(txn);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("ACTIVE");
    }

    @Test
    @DisplayName("Full flow: customer not found returns error")
    void processTransaction_customerNotFound_fails() {
        Transaction txn = transactionRepository.save(buildTxn(9999L, Transaction.TransactionType.DP, "100.00"));

        ProcessResult result = service.processTransaction(txn);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("NOT FOUND");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private Transaction buildTxn(Long custId, Transaction.TransactionType type, String amount) {
        return Transaction.builder()
                .customerId(custId)
                .type(type)
                .amount(new BigDecimal(amount))
                .date(LocalDate.now())
                .status("PENDING")
                .build();
    }
}
