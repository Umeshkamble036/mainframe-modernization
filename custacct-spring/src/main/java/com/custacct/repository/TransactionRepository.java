package com.custacct.repository;

import com.custacct.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Transaction Repository - replaces TRANSACTION-FILE (sequential PS file).
 *
 * In the original COBOL, transactions were read sequentially from a flat file.
 * Here they can be submitted via REST API or loaded from a file via the batch job.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByCustomerId(Long customerId);

    List<Transaction> findByStatus(String status);

    List<Transaction> findByDate(LocalDate date);

    /**
     * Fetch pending transactions in order (replaces sequential file read).
     * COBOL: READ TRANSACTION-FILE INTO TRANSACTION-RECORD / AT END ...
     */
    List<Transaction> findByStatusOrderByDateAscTimeAsc(String status);
}
