package com.custacct.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Batch Job Execution Result - replaces the COBOL report output file (RPTFILE).
 *
 * Stores the summary data that COBOL printed in 3100-WRITE-SUMMARY:
 *   WS-RECORDS-READ, WS-RECORDS-UPDATED, WS-RECORDS-ERRORS,
 *   WS-TOTAL-DEPOSITS, WS-TOTAL-WITHDRAWALS, WS-TOTAL-PAYMENTS.
 */
@Entity
@Table(name = "BATCH_JOB_RESULTS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchJobResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Spring Batch job execution ID */
    @Column(name = "JOB_EXECUTION_ID")
    private Long jobExecutionId;

    /** Run timestamp */
    @Column(name = "RUN_DATE_TIME", nullable = false)
    private LocalDateTime runDateTime;

    /** WS-RECORDS-READ PIC 9(7) */
    @Column(name = "RECORDS_READ")
    private int recordsRead;

    /** WS-RECORDS-UPDATED PIC 9(7) */
    @Column(name = "RECORDS_UPDATED")
    private int recordsUpdated;

    /** WS-RECORDS-SKIPPED PIC 9(7) */
    @Column(name = "RECORDS_SKIPPED")
    private int recordsSkipped;

    /** WS-RECORDS-ERRORS PIC 9(7) */
    @Column(name = "RECORDS_ERRORS")
    private int recordsErrors;

    /** WS-TOTAL-DEPOSITS PIC S9(13)V99 COMP-3 */
    @Column(name = "TOTAL_DEPOSITS", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalDeposits = BigDecimal.ZERO;

    /** WS-TOTAL-WITHDRAWALS PIC S9(13)V99 COMP-3 */
    @Column(name = "TOTAL_WITHDRAWALS", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalWithdrawals = BigDecimal.ZERO;

    /** WS-TOTAL-PAYMENTS PIC S9(13)V99 COMP-3 */
    @Column(name = "TOTAL_PAYMENTS", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPayments = BigDecimal.ZERO;

    /** COBOL RETURN-CODE (0 = success, errors = error count) */
    @Column(name = "RETURN_CODE")
    private int returnCode;

    /** Full text report (replaces RPTFILE sequential output) */
    @Lob
    @Column(name = "REPORT_TEXT")
    private String reportText;

    @Column(name = "STATUS", length = 20)
    private String status;
}
