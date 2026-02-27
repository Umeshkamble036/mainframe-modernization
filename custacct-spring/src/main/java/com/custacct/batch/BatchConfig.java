package com.custacct.batch;

import com.custacct.entity.BatchJobResult;
import com.custacct.entity.Customer;
import com.custacct.entity.Transaction;
import com.custacct.repository.BatchJobResultRepository;
import com.custacct.repository.CustomerRepository;
import com.custacct.repository.TransactionRepository;
import com.custacct.service.ReportService;
import com.custacct.service.TransactionProcessingService;
import com.custacct.service.TransactionProcessingService.AmountType;
import com.custacct.service.TransactionProcessingService.ProcessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Batch Configuration - replaces CUSTJOB.jcl
 *
 * JCL → Spring Batch mapping:
 *   //CUSTJOB  JOB ...               → @Bean Job custProcessingJob()
 *   //SORT     EXEC PGM=SORT         → transactions sorted by date/time in ItemReader
 *   //CUSTACCT EXEC PGM=CUSTACCT     → ItemProcessor (TransactionProcessingService)
 *
 * The COBOL PERFORM ... UNTIL END-OF-FILE loop is now:
 *   ItemReader  → reads pending transactions (chunk by chunk)
 *   ItemProcessor → validates + applies each transaction
 *   ItemWriter  → saves results + builds report
 *
 * Trigger via: POST /api/batch/run
 * Schedule:    Daily at midnight (see BatchScheduler)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final TransactionProcessingService processingService;
    private final ReportService reportService;
    private final BatchJobResultRepository batchJobResultRepository;

    // ----------------------------------------------------------------
    // JOB definition (replaces //CUSTJOB JOB card)
    // ----------------------------------------------------------------

    @Bean
    public Job custProcessingJob() {
        return new JobBuilder("CUSTJOB", jobRepository)
                .start(processTransactionsStep())
                .listener(jobExecutionListener())
                .build();
    }

    // ----------------------------------------------------------------
    // STEP (replaces //CUSTACCT EXEC PGM=CUSTACCT step)
    // ----------------------------------------------------------------

    @Bean
    public Step processTransactionsStep() {
        return new StepBuilder("CUSTACCT", jobRepository)
                .<Transaction, ProcessResult>chunk(50, transactionManager)
                .reader(transactionItemReader())
                .processor(transactionItemProcessor())
                .writer(transactionItemWriter())
                .build();
    }

    // ----------------------------------------------------------------
    // ITEM READER
    // Replaces: READ TRANSACTION-FILE INTO TRANSACTION-RECORD / AT END
    // Equivalent to SORT step + sequential read (sorted by date+time)
    // ----------------------------------------------------------------

    @Bean
    @StepScope
    public ItemReader<Transaction> transactionItemReader() {
        List<Transaction> pending = transactionRepository
                .findByStatusOrderByDateAscTimeAsc("PENDING");
        log.info("CUSTJOB: {} pending transactions found (replaces TRANSACTION-FILE read)", pending.size());
        return new ListItemReader<>(pending);
    }

    // ----------------------------------------------------------------
    // ITEM PROCESSOR
    // Replaces: CUSTACCT COBOL paragraphs 2100→2400
    // ----------------------------------------------------------------

    @Bean
    public ItemProcessor<Transaction, ProcessResult> transactionItemProcessor() {
        return processingService::processTransaction;
    }

    // ----------------------------------------------------------------
    // ITEM WRITER
    // Replaces: 2500-WRITE-DETAIL-LINE, 2600-WRITE-ERROR-LINE + REWRITE
    // ----------------------------------------------------------------

    @Bean
    public ItemWriter<ProcessResult> transactionItemWriter() {
        return results -> {
            // These counters mirror COBOL WS-COUNTERS
            // (accumulated in JobExecutionListener for the summary)
            for (ProcessResult result : results) {
                if (result.success()) {
                    log.debug("  PROCESSED: custId={} {} {} → balance={}",
                            result.customerId(), result.transactionType(),
                            result.amount(), result.newBalance());
                } else {
                    log.warn("  ERROR: custId={} - {}", result.customerId(), result.errorMessage());
                }
            }
        };
    }

    // ----------------------------------------------------------------
    // JOB EXECUTION LISTENER
    // Replaces: 1000-INITIALIZE and 3000-FINALIZE paragraphs
    // ----------------------------------------------------------------

    @Bean
    public JobExecutionListener jobExecutionListener() {
        return new JobExecutionListener() {

            @Override
            public void beforeJob(JobExecution jobExecution) {
                // Replaces 1000-INITIALIZE
                log.info("====================================================");
                log.info("CUSTJOB STARTED - Customer Account Processing");
                log.info("Equivalent to COBOL: PERFORM 1000-INITIALIZE");
                log.info("====================================================");
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                // Replaces 3000-FINALIZE + 3100-WRITE-SUMMARY
                log.info("====================================================");
                log.info("CUSTJOB COMPLETED - Generating report...");
                log.info("Equivalent to COBOL: PERFORM 3000-FINALIZE");

                generateAndSaveReport(jobExecution);

                log.info("====================================================");
            }
        };
    }

    // ----------------------------------------------------------------
    // Report Generation (replaces 3100-WRITE-SUMMARY + RPTFILE output)
    // ----------------------------------------------------------------

    private void generateAndSaveReport(JobExecution jobExecution) {
        // Gather stats from all processed transactions
        List<Transaction> processed = transactionRepository.findByStatus("PROCESSED");
        List<Transaction> errors    = transactionRepository.findByStatus("ERROR");

        int recordsRead    = processed.size() + errors.size();
        int recordsUpdated = processed.size();
        int recordsErrors  = errors.size();

        BigDecimal totalDeposits    = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;
        BigDecimal totalPayments    = BigDecimal.ZERO;

        // Build detail lines for the report
        StringBuilder reportSb = new StringBuilder();
        reportService.buildHeader(reportSb);

        // Gather totals (mirrors WS-COUNTERS accumulation in COBOL)
        for (Transaction txn : processed) {
            if (txn.getType() == null) continue;
            customerRepository.findById(txn.getCustomerId()).ifPresent(customer -> {
                reportService.appendDetailLine(reportSb, customer, txn, customer.getAccountBalance());
            });
            switch (txn.getType()) {
                case DP -> totalDeposits = totalDeposits.add(txn.getAmount());
                case WD, TR -> totalWithdrawals = totalWithdrawals.add(txn.getAmount());
                case PM -> totalPayments = totalPayments.add(txn.getAmount());
            }
        }

        // Error lines
        for (Transaction txn : errors) {
            reportService.appendErrorLine(reportSb, txn.getCustomerId(), txn.getErrorMessage());
        }

        // Summary (mirrors 3100-WRITE-SUMMARY)
        reportService.buildSummary(reportSb,
                recordsRead, recordsUpdated, recordsErrors,
                totalDeposits, totalWithdrawals, totalPayments);

        String reportText = reportSb.toString();

        // Log summary (same as DISPLAY statements in COBOL)
        log.info("RECORDS READ:        {}", recordsRead);
        log.info("RECORDS UPDATED:     {}", recordsUpdated);
        log.info("RECORDS IN ERROR:    {}", recordsErrors);
        log.info("TOTAL DEPOSITS:      {}", totalDeposits);
        log.info("TOTAL WITHDRAWALS:   {}", totalWithdrawals);
        log.info("TOTAL PAYMENTS:      {}", totalPayments);

        // Save to DB (replaces writing to RPTFILE)
        BatchJobResult result = BatchJobResult.builder()
                .jobExecutionId(jobExecution.getId())
                .runDateTime(LocalDateTime.now())
                .recordsRead(recordsRead)
                .recordsUpdated(recordsUpdated)
                .recordsErrors(recordsErrors)
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .totalPayments(totalPayments)
                .returnCode(recordsErrors) // COBOL: MOVE WS-RECORDS-ERRORS TO RETURN-CODE
                .reportText(reportText)
                .status(jobExecution.getStatus().name())
                .build();

        batchJobResultRepository.save(result);
        log.info("Report saved. GET /api/batch/report to view.");
    }
}
