package com.custacct.controller;

import com.custacct.batch.BatchScheduler;
import com.custacct.batch.TransactionFileLoader;
import com.custacct.entity.BatchJobResult;
import com.custacct.repository.BatchJobResultRepository;
import com.custacct.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Batch Job REST Controller
 *
 * Provides HTTP control over the CUSTJOB batch processing.
 *
 * In the original system, jobs were submitted via:
 *   aws m2 start-batch-job --batch-job-identifier "jobName=CUSTJOB"
 *
 * Now they can be triggered via simple REST calls:
 *   POST /api/batch/run         — trigger the batch job manually
 *   GET  /api/batch/report      — get the latest text report (replaces cat ACCOUNT.REPORT.dat)
 *   GET  /api/batch/results     — get all batch run history
 *   POST /api/batch/load-file   — upload a DAILY.TRANSACTIONS.RAW.dat file
 */
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchJobController {

    private final BatchScheduler batchScheduler;
    private final BatchJobResultRepository batchJobResultRepository;
    private final ReportService reportService;
    private final TransactionFileLoader transactionFileLoader;

    /**
     * Trigger the batch job manually.
     *
     * Replaces:
     *   aws m2 start-batch-job --batch-job-identifier "jobName=CUSTJOB"
     *   OR: Submit job from AWS M2 Console
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runBatchJob() {
        BatchStatus status = batchScheduler.runJobManually();
        return ResponseEntity.ok(Map.of(
                "status", status.name(),
                "message", status == BatchStatus.COMPLETED
                        ? "CUSTJOB completed successfully. GET /api/batch/report to view results."
                        : "CUSTJOB status: " + status.name()
        ));
    }

    /**
     * Get the latest batch report as plain text.
     *
     * Replaces:
     *   aws s3 cp s3://output-bucket/reports/ACCOUNT.REPORT.dat .
     *   cat ACCOUNT.REPORT.dat
     */
    @GetMapping(value = "/report", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLatestReport() {
        return reportService.getLatestReport();
    }

    /**
     * Get all batch run results (history).
     * Replaces: aws m2 list-batch-job-executions
     */
    @GetMapping("/results")
    public List<BatchJobResult> getAllResults() {
        return batchJobResultRepository.findAll();
    }

    /**
     * Get latest batch run result summary (without full report text).
     */
    @GetMapping("/results/latest")
    public ResponseEntity<?> getLatestResult() {
        return batchJobResultRepository.findTopByOrderByRunDateTimeDesc()
                .map(r -> ResponseEntity.ok(Map.of(
                        "runDateTime", r.getRunDateTime().toString(),
                        "recordsRead", r.getRecordsRead(),
                        "recordsUpdated", r.getRecordsUpdated(),
                        "recordsErrors", r.getRecordsErrors(),
                        "totalDeposits", r.getTotalDeposits(),
                        "totalWithdrawals", r.getTotalWithdrawals(),
                        "totalPayments", r.getTotalPayments(),
                        "returnCode", r.getReturnCode(),
                        "status", r.getStatus()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of("message", "No batch runs yet")));
    }

    /**
     * Upload a DAILY.TRANSACTIONS.RAW.dat file and load its contents.
     *
     * Replaces:
     *   aws s3 cp DAILY.TRANSACTIONS.RAW.dat s3://input-bucket/daily-transactions/
     *   (Then the M2 job picks it up from S3)
     */
    @PostMapping("/load-file")
    public ResponseEntity<Map<String, Object>> loadTransactionFile(
            @RequestParam("file") MultipartFile file) throws IOException {

        File tempFile = File.createTempFile("transactions_", ".dat");
        file.transferTo(tempFile);

        int count = transactionFileLoader.loadFromFile(tempFile.getAbsolutePath());
        tempFile.delete();

        return ResponseEntity.ok(Map.of(
                "message", "Transactions loaded successfully",
                "count", count,
                "nextStep", "POST /api/batch/run to process them"
        ));
    }
}
