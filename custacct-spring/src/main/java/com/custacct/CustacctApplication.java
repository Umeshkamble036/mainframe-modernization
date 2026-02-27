package com.custacct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Customer Account Management System
 *
 * Java/Spring Boot transformation of the COBOL CUSTACCT mainframe program.
 *
 * Original COBOL program processed:
 *   - CUSTOMER-FILE  (VSAM KSDS)     → now H2/JPA (swap to PostgreSQL for prod)
 *   - TRANSACTION-FILE (Sequential)  → now flat-file reader via Spring Batch
 *   - REPORT-FILE (Sequential)       → now REST endpoint + in-memory report
 *
 * The batch job (CUSTJOB in JCL) is now a Spring Batch job triggered via:
 *   POST /api/batch/run
 *
 * Daily scheduling is wired via Spring @Scheduled (see BatchScheduler).
 */
@SpringBootApplication
public class CustacctApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustacctApplication.class, args);
    }
}
