package com.custacct.service;

import com.custacct.entity.Customer;
import com.custacct.entity.Transaction;
import com.custacct.repository.CustomerRepository;
import com.custacct.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Transaction Processing Service
 *
 * This is the heart of the application — a direct Java translation of the
 * COBOL PROCEDURE DIVISION in CUSTACCT.cbl.
 *
 * COBOL paragraph → Java method mapping:
 *   2100-LOOKUP-CUSTOMER         → lookupCustomer()
 *   2200-VALIDATE-TRANSACTION    → validateTransaction()
 *   2300-APPLY-TRANSACTION       → applyTransaction()
 *   2400-UPDATE-CUSTOMER-RECORD  → updateCustomerRecord()
 *   2000-PROCESS-TRANSACTIONS    → processTransaction()
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessingService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    // ----------------------------------------------------------------
    // 2000-PROCESS-TRANSACTIONS (main entry point per record)
    // ----------------------------------------------------------------

    /**
     * Process a single transaction record.
     * Mirrors the COBOL paragraph 2000-PROCESS-TRANSACTIONS:
     *
     *   PERFORM 2100-LOOKUP-CUSTOMER
     *   EVALUATE TRUE
     *     WHEN RECORD-FOUND  → validate + apply + update + write-detail
     *     WHEN RECORD-NOT-FOUND → write-error
     *   PERFORM 1200-READ-NEXT-TRANSACTION
     *
     * @return ProcessResult with outcome details
     */
    @Transactional
    public ProcessResult processTransaction(Transaction txn) {
        log.debug("Processing transaction: custId={}, type={}, amount={}",
                txn.getCustomerId(), txn.getType(), txn.getAmount());

        // 2100-LOOKUP-CUSTOMER
        Optional<Customer> customerOpt = lookupCustomer(txn.getCustomerId());

        if (customerOpt.isEmpty()) {
            // WHEN RECORD-NOT-FOUND
            String msg = "CUSTOMER NOT FOUND IN MASTER FILE";
            log.warn("Transaction skipped - {}: custId={}", msg, txn.getCustomerId());
            markTransactionFailed(txn, msg);
            return ProcessResult.error(txn.getCustomerId(), msg);
        }

        Customer customer = customerOpt.get();

        // 2200-VALIDATE-TRANSACTION
        ValidationResult validation = validateTransaction(customer, txn);
        if (!validation.valid()) {
            log.warn("Validation failed for custId={}: {}", txn.getCustomerId(), validation.message());
            markTransactionFailed(txn, validation.message());
            return ProcessResult.error(txn.getCustomerId(), validation.message());
        }

        // 2300-APPLY-TRANSACTION
        ApplyResult applyResult = applyTransaction(customer, txn);
        if (!applyResult.success()) {
            markTransactionFailed(txn, applyResult.message());
            return ProcessResult.error(txn.getCustomerId(), applyResult.message());
        }

        // 2400-UPDATE-CUSTOMER-RECORD
        updateCustomerRecord(customer, txn);

        // Mark transaction as processed
        txn.setStatus("PROCESSED");
        transactionRepository.save(txn);

        log.debug("Transaction processed successfully: custId={}, newBalance={}",
                customer.getId(), customer.getAccountBalance());

        return ProcessResult.success(
                customer,
                txn,
                applyResult.amountType()
        );
    }

    // ----------------------------------------------------------------
    // 2100-LOOKUP-CUSTOMER
    //   MOVE TXN-CUST-ID TO CUST-ID
    //   READ CUSTOMER-FILE INVALID KEY ... NOT INVALID KEY ...
    // ----------------------------------------------------------------

    /**
     * Paragraph 2100-LOOKUP-CUSTOMER
     * VSAM KSDS key read → JPA findById
     */
    public Optional<Customer> lookupCustomer(Long customerId) {
        return customerRepository.findById(customerId);
    }

    // ----------------------------------------------------------------
    // 2200-VALIDATE-TRANSACTION
    // ----------------------------------------------------------------

    /**
     * Paragraph 2200-VALIDATE-TRANSACTION.
     *
     * COBOL rules translated:
     *   WHEN TXN-AMOUNT <= ZEROS → "TRANSACTION AMOUNT MUST BE POSITIVE"
     *   WHEN NOT CUST-ACTIVE     → "ACCOUNT IS NOT IN ACTIVE STATUS"
     *   WHEN TXN-WITHDRAWAL AND TXN-AMOUNT > CUST-ACCOUNT-BAL → "INSUFFICIENT FUNDS"
     *   WHEN TXN-WITHDRAWAL AND balance - amount < CUST-MIN-BALANCE → "WOULD BREACH MIN BALANCE"
     */
    public ValidationResult validateTransaction(Customer customer, Transaction txn) {

        // WHEN TXN-AMOUNT <= ZEROS
        if (txn.getAmount() == null || txn.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.ofInvalid("TRANSACTION AMOUNT MUST BE POSITIVE");
        }

        // WHEN NOT CUST-ACTIVE (status != 'A')
        if (!customer.isActive()) {
            return ValidationResult.ofInvalid("ACCOUNT IS NOT IN ACTIVE STATUS");
        }

        // Withdrawal-specific checks
        if (txn.getType() == Transaction.TransactionType.WD ||
            txn.getType() == Transaction.TransactionType.TR) {

            // WHEN TXN-WITHDRAWAL AND TXN-AMOUNT > CUST-ACCOUNT-BAL
            if (txn.getAmount().compareTo(customer.getAccountBalance()) > 0) {
                return ValidationResult.ofInvalid("INSUFFICIENT FUNDS FOR WITHDRAWAL");
            }

            // WHEN TXN-WITHDRAWAL AND CUST-ACCOUNT-BAL - TXN-AMOUNT < CUST-MIN-BALANCE
            BigDecimal balanceAfter = customer.getAccountBalance().subtract(txn.getAmount());
            BigDecimal minBalance = customer.getMinBalance() != null
                    ? customer.getMinBalance() : BigDecimal.ZERO;
            if (balanceAfter.compareTo(minBalance) < 0) {
                return ValidationResult.ofInvalid("WITHDRAWAL WOULD BREACH MINIMUM BALANCE");
            }
        }

        return ValidationResult.ofValid();
    }

    // ----------------------------------------------------------------
    // 2300-APPLY-TRANSACTION
    // ----------------------------------------------------------------

    /**
     * Paragraph 2300-APPLY-TRANSACTION.
     *
     * COBOL logic translated:
     *   WHEN TXN-DEPOSIT    → ADD TXN-AMOUNT TO CUST-ACCOUNT-BAL
     *   WHEN TXN-WITHDRAWAL → SUBTRACT TXN-AMOUNT FROM CUST-ACCOUNT-BAL
     *   WHEN TXN-PAYMENT    → SUBTRACT, floor at zero (debt payoff)
     *   WHEN TXN-TRANSFER   → SUBTRACT TXN-AMOUNT FROM CUST-ACCOUNT-BAL
     *   WHEN OTHER          → error "UNKNOWN TRANSACTION TYPE CODE"
     */
    public ApplyResult applyTransaction(Customer customer, Transaction txn) {
        BigDecimal amount = txn.getAmount();

        switch (txn.getType()) {
            case DP: // 88 TXN-DEPOSIT — ADD TXN-AMOUNT TO CUST-ACCOUNT-BAL
                customer.setAccountBalance(customer.getAccountBalance().add(amount));
                return ApplyResult.ofSuccess(AmountType.DEPOSIT);

            case WD: // 88 TXN-WITHDRAWAL — SUBTRACT TXN-AMOUNT FROM CUST-ACCOUNT-BAL
                customer.setAccountBalance(customer.getAccountBalance().subtract(amount));
                return ApplyResult.ofSuccess(AmountType.WITHDRAWAL);

            case PM: // 88 TXN-PAYMENT — SUBTRACT, floor at 0
                BigDecimal newBalance = customer.getAccountBalance().subtract(amount);
                // IF CUST-ACCOUNT-BAL < ZEROS MOVE ZEROS TO CUST-ACCOUNT-BAL
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    newBalance = BigDecimal.ZERO;
                }
                customer.setAccountBalance(newBalance);
                return ApplyResult.ofSuccess(AmountType.PAYMENT);

            case TR: // 88 TXN-TRANSFER — SUBTRACT TXN-AMOUNT FROM CUST-ACCOUNT-BAL
                customer.setAccountBalance(customer.getAccountBalance().subtract(amount));
                return ApplyResult.ofSuccess(AmountType.WITHDRAWAL); // counts as withdrawal in totals

            default:
                return ApplyResult.ofFailure("UNKNOWN TRANSACTION TYPE CODE");
        }
    }

    // ----------------------------------------------------------------
    // 2400-UPDATE-CUSTOMER-RECORD
    //   MOVE TXN-DATE TO CUST-LAST-TXN-DATE
    //   ADD 1 TO CUST-TXN-COUNT
    //   REWRITE CUSTOMER-RECORD
    // ----------------------------------------------------------------

    /**
     * Paragraph 2400-UPDATE-CUSTOMER-RECORD.
     * VSAM REWRITE → JPA save (update via dirty-checking in @Transactional).
     */
    public void updateCustomerRecord(Customer customer, Transaction txn) {
        // MOVE TXN-DATE TO CUST-LAST-TXN-DATE
        if (txn.getDate() != null) {
            customer.setLastTransactionDate(txn.getDate());
        } else {
            customer.setLastTransactionDate(LocalDate.now());
        }
        // ADD 1 TO CUST-TXN-COUNT
        customer.setTransactionCount(customer.getTransactionCount() + 1);

        // REWRITE CUSTOMER-RECORD (NOT INVALID KEY → ADD 1 TO WS-RECORDS-UPDATED)
        customerRepository.save(customer);
    }

    // ----------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------

    private void markTransactionFailed(Transaction txn, String message) {
        txn.setStatus("ERROR");
        txn.setErrorMessage(message);
        transactionRepository.save(txn);
    }

    // ----------------------------------------------------------------
    // Result value objects
    // ----------------------------------------------------------------

    public enum AmountType { DEPOSIT, WITHDRAWAL, PAYMENT }

    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ofValid() { return new ValidationResult(true, null); }
        public static ValidationResult ofInvalid(String msg) { return new ValidationResult(false, msg); }
    }

    public record ApplyResult(boolean success, String message, AmountType amountType) {
        public static ApplyResult ofSuccess(AmountType type) { return new ApplyResult(true, null, type); }
        public static ApplyResult ofFailure(String msg) { return new ApplyResult(false, msg, null); }
    }

    public record ProcessResult(
            boolean success,
            Long customerId,
            String customerName,
            String transactionType,
            BigDecimal amount,
            BigDecimal newBalance,
            String errorMessage
    ) {
        static ProcessResult success(Customer c, Transaction t, AmountType at) {
            return new ProcessResult(true, c.getId(), c.getFullName(),
                    t.getType().getDisplayName(), t.getAmount(), c.getAccountBalance(), null);
        }
        static ProcessResult error(Long custId, String msg) {
            return new ProcessResult(false, custId, null, null, null, null, msg);
        }
    }
}
