package com.custacct.controller;

import com.custacct.dto.TransactionRequest;
import com.custacct.entity.Customer;
import com.custacct.entity.Transaction;
import com.custacct.repository.CustomerRepository;
import com.custacct.repository.TransactionRepository;
import com.custacct.service.TransactionProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Customer REST Controller
 *
 * Exposes the VSAM customer master file and real-time transaction processing
 * as a REST API. This did not exist in the original COBOL batch system —
 * it's a new capability added by the Java transformation.
 *
 * Endpoints:
 *   GET    /api/customers              — list all customers (replaces CUSTFILE browse)
 *   GET    /api/customers/{id}         — get by ID (replaces READ CUSTOMER-FILE)
 *   GET    /api/customers/email/{email} — get by email (replaces alternate key read)
 *   POST   /api/customers/{id}/transactions — submit real-time transaction
 *   GET    /api/customers/{id}/transactions — list customer's transactions
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionProcessingService processingService;

    /**
     * List all customers.
     * Replaces: browse/sequential read of CUSTOMER-FILE.
     */
    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    /**
     * Get customer by ID (CUST-ID primary key lookup).
     * Replaces: READ CUSTOMER-FILE / INVALID KEY handling.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get customer by email (alternate VSAM key lookup).
     * Replaces: READ CUSTOMER-FILE KEY IS CUST-EMAIL.
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<List<Customer>> getCustomerByEmail(@PathVariable String email) {
        List<Customer> customers = customerRepository.findByEmail(email);
        return customers.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(customers);
    }

    /**
     * Submit a real-time transaction for immediate processing.
     *
     * In the original COBOL system, transactions were batched into a flat file
     * and processed nightly. This endpoint enables real-time processing —
     * the same COBOL business rules (2200-VALIDATE + 2300-APPLY) are applied
     * immediately by TransactionProcessingService.
     */
    @PostMapping("/{id}/transactions")
    public ResponseEntity<?> submitTransaction(
            @PathVariable Long id,
            @RequestBody @Valid TransactionRequest req) {

        if (!customerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // Build transaction entity (replaces writing a record to TRANFILE)
        Transaction txn = Transaction.builder()
                .customerId(id)
                .type(req.getType())
                .amount(req.getAmount())
                .date(req.getDate() != null ? req.getDate() : LocalDate.now())
                .time(req.getTime() != null ? req.getTime() : LocalTime.now())
                .reference(req.getReference())
                .description(req.getDescription())
                .channel(req.getChannel())
                .status("PENDING")
                .build();

        txn = transactionRepository.save(txn);

        // Process immediately (inline equivalent of CUSTACCT batch logic)
        TransactionProcessingService.ProcessResult result = processingService.processTransaction(txn);

        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "status", "PROCESSED",
                    "customerId", result.customerId(),
                    "customerName", result.customerName(),
                    "transactionType", result.transactionType(),
                    "amount", result.amount(),
                    "newBalance", result.newBalance()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "status", "ERROR",
                    "customerId", id,
                    "error", result.errorMessage()
            ));
        }
    }

    /**
     * List all transactions for a customer.
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<Transaction>> getCustomerTransactions(@PathVariable Long id) {
        if (!customerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(transactionRepository.findByCustomerId(id));
    }
}
