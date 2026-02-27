package com.custacct.service;

import com.custacct.entity.BatchJobResult;
import com.custacct.entity.Customer;
import com.custacct.entity.Transaction;
import com.custacct.repository.BatchJobResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Report Service - replaces COBOL REPORT-FILE (RPTFILE) output.
 *
 * Generates the same fixed-width 132-column report that the COBOL program
 * wrote to the sequential report file, now exposed via REST API.
 *
 * COBOL paragraphs translated:
 *   1100-WRITE-REPORT-HEADERS → buildHeader()
 *   2500-WRITE-DETAIL-LINE    → appendDetailLine()
 *   2600-WRITE-ERROR-LINE     → appendErrorLine()
 *   3100-WRITE-SUMMARY        → buildSummary()
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int REPORT_WIDTH = 132;
    private static final String SEPARATOR = "-".repeat(REPORT_WIDTH);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final BatchJobResultRepository batchJobResultRepository;

    // ----------------------------------------------------------------
    // 1100-WRITE-REPORT-HEADERS
    // ----------------------------------------------------------------

    public StringBuilder buildHeader(StringBuilder sb) {
        String today = LocalDate.now().format(DATE_FMT);

        // WS-REPORT-HEADER-1
        sb.append(String.format("%-45s%-40s%-10s%n",
                "         CUSTOMER ACCOUNT MANAGEMENT REPORT", "", today));

        // WS-REPORT-HEADER-2
        sb.append(String.format("%-10s%-2s%-35s%-10s%-2s%-15s%-2s%-15s%n",
                "CUST-ID", "", "CUSTOMER NAME", "TXN-TYPE", "", "AMOUNT", "", "NEW BALANCE"));

        sb.append(SEPARATOR).append("\n");
        return sb;
    }

    // ----------------------------------------------------------------
    // 2500-WRITE-DETAIL-LINE
    // ----------------------------------------------------------------

    /**
     * Paragraph 2500-WRITE-DETAIL-LINE.
     *
     * COBOL format:
     *   WS-DL-CUST-ID  Z(9)9
     *   WS-DL-NAME     X(35)
     *   WS-DL-TXN-TYPE X(10)
     *   WS-DL-AMOUNT   ZZZ,ZZZ,ZZ9.99-
     *   WS-DL-BALANCE  ZZZ,ZZZ,ZZ9.99-
     */
    public StringBuilder appendDetailLine(StringBuilder sb, Customer customer,
                                          Transaction txn, BigDecimal newBalance) {
        String txnType = txn.getType().getDisplayName();
        String name = String.format("%-35s", customer.getFullName());
        String amount = formatAmount(txn.getAmount());
        String balance = formatAmount(newBalance);

        sb.append(String.format("%-10d  %-35s  %-10s  %15s  %15s%n",
                customer.getId(), name.trim(), txnType, amount, balance));
        return sb;
    }

    // ----------------------------------------------------------------
    // 2600-WRITE-ERROR-LINE
    // ----------------------------------------------------------------

    /**
     * Paragraph 2600-WRITE-ERROR-LINE.
     *
     * COBOL format:
     *   FILLER '** ERR: '
     *   WS-ERR-CUST-ID  9(10)
     *   FILLER ' - '
     *   WS-ERR-MESSAGE  X(60)
     */
    public StringBuilder appendErrorLine(StringBuilder sb, Long custId, String errorMessage) {
        sb.append(String.format("** ERR: %10d - %-60s%n", custId, errorMessage));
        return sb;
    }

    // ----------------------------------------------------------------
    // 3100-WRITE-SUMMARY
    // ----------------------------------------------------------------

    /**
     * Paragraph 3100-WRITE-SUMMARY.
     *
     * COBOL WS-SUMMARY-LINE-1 through WS-SUMMARY-LINE-5 translated.
     */
    public StringBuilder buildSummary(StringBuilder sb, int recordsRead, int recordsUpdated,
                                       int recordsErrors, BigDecimal totalDeposits,
                                       BigDecimal totalWithdrawals, BigDecimal totalPayments) {
        sb.append(SEPARATOR).append("\n");
        sb.append(String.format("%-30s%,9d%n", "RECORDS READ:", recordsRead));
        sb.append(String.format("%-30s%,9d%n", "RECORDS UPDATED:", recordsUpdated));
        sb.append(String.format("%-30s%,9d%n", "RECORDS IN ERROR:", recordsErrors));
        sb.append(String.format("%-30s%18s%n", "TOTAL DEPOSITS:", formatAmount(totalDeposits)));
        sb.append(String.format("%-30s%18s%n", "TOTAL WITHDRAWALS:", formatAmount(totalWithdrawals)));
        sb.append(String.format("%-30s%18s%n", "TOTAL PAYMENTS:", formatAmount(totalPayments)));
        return sb;
    }

    /**
     * Retrieve the most recent batch job report.
     * Replaces: cat ACCOUNT.REPORT.dat (reading the output file)
     */
    public String getLatestReport() {
        return batchJobResultRepository.findTopByOrderByRunDateTimeDesc()
                .map(BatchJobResult::getReportText)
                .orElse("No batch job has been run yet. POST /api/batch/run to start one.");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Format BigDecimal as COBOL ZZZ,ZZZ,ZZ9.99- picture.
     * Negative values show trailing minus sign.
     */
    String formatAmount(BigDecimal value) {
        if (value == null) return "0.00";
        boolean negative = value.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal abs = value.abs();
        String formatted = String.format("%,.2f", abs);
        return negative ? formatted + "-" : formatted;
    }
}
