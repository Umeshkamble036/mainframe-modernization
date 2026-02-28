package com.custacct.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Batch Scheduler - replaces batch-scheduler.yaml (AWS EventBridge / CloudWatch Events).
 *
 * In the original setup:
 *   AWS EventBridge → triggers M2 batch job daily at 01:00 UTC
 *   Shell: aws m2 start-batch-job --batch-job-identifier "jobName=CUSTJOB"
 *
 * Now: Spring @Scheduled cron expression fires the same job daily.
 * Disable for manual-only mode by removing @EnableScheduling or setting
 *   custacct.batch.schedule.enabled=false in application.properties.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduler.class);
    private final JobLauncher jobLauncher;
    private final Job custProcessingJob;

    /**
     * Daily job run at 01:00 local time.
     * Equivalent to: schedule: rate(1 day) in batch-scheduler.yaml.
     *
     * Change via: custacct.batch.cron=0 0 1 * * ? in application.properties
     */
    @Scheduled(cron = "${custacct.batch.cron:0 0 1 * * ?}")
    public void runDailyBatchJob() {
        log.info("=== Scheduled CUSTJOB starting (daily trigger) ===");
        runJob("SCHEDULED");
    }

    /**
     * Manual trigger for the batch job.
     * Called by POST /api/batch/run in BatchJobController.
     */
    public BatchStatus runJobManually() {
        return runJob("MANUAL");
    }

    BatchStatus runJob(String triggerType) {
        String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        log.info("Launching CUSTJOB [trigger={}, runId={}]", triggerType, runId);

        JobParameters params = new JobParametersBuilder()
                .addString("triggerType", triggerType)
                .addString("runId", runId)
                .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(custProcessingJob, params);
            log.info("CUSTJOB completed: status={}, exitCode={}",
                    execution.getStatus(), execution.getExitStatus().getExitCode());
            return execution.getStatus();
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("CUSTJOB already running — skipping duplicate launch");
            return BatchStatus.STARTED;
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("CUSTJOB already completed for these parameters");
            return BatchStatus.COMPLETED;
        } catch (JobRestartException | JobParametersInvalidException e) {
            log.error("CUSTJOB failed to launch: {}", e.getMessage());
            return BatchStatus.FAILED;
        }
    }
}
