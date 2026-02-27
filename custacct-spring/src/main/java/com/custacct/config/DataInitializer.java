package com.custacct.config;

import com.custacct.entity.Customer;
import com.custacct.entity.Transaction;
import com.custacct.repository.CustomerRepository;
import com.custacct.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Sample Data Initializer
 *
 * Loads the 10 sample customers and 20 transactions from CUSTOMER.MASTER.dat
 * and DAILY.TRANSACTIONS.RAW.dat into the H2 database on startup.
 *
 * This replaces the VSAM dataset import process:
 *   aws m2 create-data-set-import-task --import-config dataset-import.yaml
 *
 * In production, replace this with:
 *   1. A real PostgreSQL database with proper schema migration (Flyway/Liquibase)
 *   2. The TransactionFileLoader to load transaction files from disk/S3
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    @Override
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            log.info("Data already loaded — skipping initialization");
            return;
        }

        log.info("Loading sample data from CUSTOMER.MASTER.dat and DAILY.TRANSACTIONS.RAW.dat...");
        loadCustomers();
        loadTransactions();
        log.info("Sample data loaded: {} customers, {} transactions",
                customerRepository.count(), transactionRepository.count());
    }

    // ----------------------------------------------------------------
    // CUSTOMER.MASTER.dat — 10 sample customers
    // ----------------------------------------------------------------

    private void loadCustomers() {
        customerRepository.save(Customer.builder()
                .id(1L)
                .lastName("SMITH").firstName("JOHN")
                .street("123 MAIN ST").city("AUSTIN").state("TX").zipCode("78701")
                .phone("5550123456").email("jsmith@email.com")
                .accountBalance(new BigDecimal("500.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .minBalance(new BigDecimal("100.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2020, 1, 15))
                .lastTransactionDate(LocalDate.of(2024, 1, 15))
                .transactionCount(11).branchCode(1001)
                .build());

        customerRepository.save(Customer.builder()
                .id(2L)
                .lastName("JOHNSON").firstName("MARY")
                .street("456 OAK AVE").city("DALLAS").state("TX").zipCode("75201")
                .phone("5550987654").email("mjohnson@email.com")
                .accountBalance(new BigDecimal("1250.50"))
                .creditLimit(new BigDecimal("10000.00"))
                .minBalance(new BigDecimal("500.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2018, 3, 20))
                .lastTransactionDate(LocalDate.of(2024, 1, 8))
                .transactionCount(21).branchCode(1002)
                .build());

        customerRepository.save(Customer.builder()
                .id(3L)
                .lastName("WILLIAMS").firstName("ROBERT")
                .street("789 ELM BLVD").city("HOUSTON").state("TX").zipCode("77001")
                .phone("5551112222").email("rwilliams@email.com")
                .accountBalance(new BigDecimal("7500.00"))
                .creditLimit(new BigDecimal("20000.00"))
                .minBalance(new BigDecimal("1000.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2019, 5, 1))
                .lastTransactionDate(LocalDate.of(2024, 1, 12))
                .transactionCount(181).branchCode(1003)
                .build());

        customerRepository.save(Customer.builder()
                .id(4L)
                .lastName("JONES").firstName("PATRICIA")
                .street("321 PINE RD").city("SAN ANTONIO").state("TX").zipCode("78201")
                .phone("5553334444").email("pjones@email.com")
                .accountBalance(new BigDecimal("250.00"))
                .creditLimit(new BigDecimal("2500.00"))
                .minBalance(new BigDecimal("100.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2022, 3, 10))
                .lastTransactionDate(LocalDate.of(2024, 1, 10))
                .transactionCount(5).branchCode(1001)
                .build());

        customerRepository.save(Customer.builder()
                .id(5L)
                .lastName("BROWN").firstName("MICHAEL")
                .street("654 CEDAR LN").city("FORT WORTH").state("TX").zipCode("76101")
                .phone("5555556666").email("mbrown@email.com")
                .accountBalance(new BigDecimal("15000.00"))
                .creditLimit(new BigDecimal("50000.00"))
                .minBalance(new BigDecimal("5000.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2017, 1, 25))
                .lastTransactionDate(LocalDate.of(2024, 1, 15))
                .transactionCount(342).branchCode(2001)
                .build());

        customerRepository.save(Customer.builder()
                .id(6L)
                .lastName("DAVIS").firstName("LINDA")
                .street("987 MAPLE DR").city("EL PASO").state("TX").zipCode("79901")
                .phone("5557778888").email("ldavis@email.com")
                .accountBalance(new BigDecimal("0.00"))
                .creditLimit(new BigDecimal("1000.00"))
                .minBalance(new BigDecimal("0.00"))
                .status(Customer.CustomerStatus.INACTIVE)  // status 'I' — CUST-INACTIVE
                .openDate(LocalDate.of(2021, 5, 1))
                .lastTransactionDate(LocalDate.of(2023, 6, 1))
                .transactionCount(1).branchCode(1001)
                .build());

        customerRepository.save(Customer.builder()
                .id(7L)
                .lastName("MILLER").firstName("JAMES")
                .street("147 BIRCH CT").city("CORPUS CHRISTI").state("TX").zipCode("78401")
                .phone("5559990000").email("jmiller@email.com")
                .accountBalance(new BigDecimal("3000.00"))
                .creditLimit(new BigDecimal("15000.00"))
                .minBalance(new BigDecimal("1000.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2020, 8, 15))
                .lastTransactionDate(LocalDate.of(2024, 1, 13))
                .transactionCount(9).branchCode(1002)
                .build());

        customerRepository.save(Customer.builder()
                .id(8L)
                .lastName("WILSON").firstName("BARBARA")
                .street("258 SPRUCE WAY").city("LUBBOCK").state("TX").zipCode("79401")
                .phone("5552211334").email("bwilson@email.com")
                .accountBalance(new BigDecimal("800.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .minBalance(new BigDecimal("250.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2019, 12, 30))
                .lastTransactionDate(LocalDate.of(2024, 1, 11))
                .transactionCount(3).branchCode(1001)
                .build());

        customerRepository.save(Customer.builder()
                .id(9L)
                .lastName("MOORE").firstName("CHARLES")
                .street("369 WILLOW AVE").city("GARLAND").state("TX").zipCode("75041")
                .phone("5554433665").email("cmoore@email.com")
                .accountBalance(new BigDecimal("4500.00"))
                .creditLimit(new BigDecimal("20000.00"))
                .minBalance(new BigDecimal("1500.00"))
                .status(Customer.CustomerStatus.ACTIVE)
                .openDate(LocalDate.of(2021, 6, 18))
                .lastTransactionDate(LocalDate.of(2024, 1, 14))
                .transactionCount(12).branchCode(1003)
                .build());

        customerRepository.save(Customer.builder()
                .id(10L)
                .lastName("TAYLOR").firstName("SUSAN")
                .street("741 ASPEN BLVD").city("IRVING").state("TX").zipCode("75061")
                .phone("5556644778").email("staylor@email.com")
                .accountBalance(new BigDecimal("900.00"))
                .creditLimit(new BigDecimal("7500.00"))
                .minBalance(new BigDecimal("250.00"))
                .status(Customer.CustomerStatus.SUSPENDED)  // status 'S' — CUST-SUSPENDED
                .openDate(LocalDate.of(2020, 7, 4))
                .lastTransactionDate(LocalDate.of(2023, 12, 15))
                .transactionCount(2).branchCode(1001)
                .build());
    }

    // ----------------------------------------------------------------
    // DAILY.TRANSACTIONS.RAW.dat — 20 sample transactions (all PENDING)
    // ----------------------------------------------------------------

    private void loadTransactions() {
        LocalDate txnDate = LocalDate.of(2024, 1, 15);

        Object[][] txns = {
            // {custId, type, amount, time, reference, description, channel}
            {1L,  "DP", "100.00", "09:30:45", "TXN-2024011500001", "Payroll Direct Deposit",         "BRN"},
            {1L,  "WD", "50.00",  "09:45:12", "TXN-2024011500002", "ATM Cash Withdrawal",             "ATM"},
            {2L,  "DP", "200.00", "10:00:30", "TXN-2024011500003", "Wire Transfer Received",          "WEB"},
            {3L,  "WD", "75.00",  "10:15:45", "TXN-2024011500004", "Online Bill Payment - Electric",  "WEB"},
            {3L,  "DP", "500.00", "10:22:00", "TXN-2024011500005", "Check Deposit",                   "BRN"},
            {4L,  "PM", "100.00", "10:30:00", "TXN-2024011500006", "Credit Card Payment",             "MOB"},
            {5L,  "DP", "1000.00","10:35:15", "TXN-2024011500007", "Business Revenue Deposit",        "BRN"},
            {5L,  "WD", "300.00", "10:40:00", "TXN-2024011500008", "Rent Payment Transfer",           "WEB"},
            {6L,  "DP", "50.00",  "10:45:30", "TXN-2024011500009", "Cash Deposit",                    "BRN"},  // INACTIVE — will error
            {7L,  "WD", "250.00", "10:50:45", "TXN-2024011500010", "Grocery Store Purchase",          "ATM"},
            {7L,  "DP", "150.00", "11:00:00", "TXN-2024011500011", "Salary Deposit",                  "WEB"},
            {8L,  "PM", "150.00", "11:05:15", "TXN-2024011500012", "Loan Payment",                    "MOB"},
            {9L,  "DP", "200.00", "11:10:30", "TXN-2024011500013", "Investment Returns",               "WEB"},
            {9L,  "WD", "100.00", "11:15:45", "TXN-2024011500014", "Utility Payment",                 "WEB"},
            {10L, "DP", "250.00", "11:21:00", "TXN-2024011500015", "Part-time Work Income",            "BRN"},  // SUSPENDED — will error
            {1L,  "TR", "300.00", "11:26:15", "TXN-2024011500016", "Internal Transfer to Savings",    "WEB"},
            {2L,  "WD", "450.00", "11:31:30", "TXN-2024011500017", "Restaurant - Downtown",            "ATM"},
            {3L,  "PM", "200.00", "11:36:45", "TXN-2024011500018", "Insurance Premium Payment",        "MOB"},
            {4L,  "DP", "750.00", "11:42:00", "TXN-2024011500019", "Freelance Payment Received",      "WEB"},
            {5L,  "WD", "500.00", "11:47:15", "TXN-2024011500020", "Fuel - Highway Gas Station",      "ATM"},
        };

        for (Object[] t : txns) {
            transactionRepository.save(Transaction.builder()
                    .customerId((Long) t[0])
                    .type(Transaction.TransactionType.valueOf((String) t[1]))
                    .amount(new BigDecimal((String) t[2]))
                    .date(txnDate)
                    .time(LocalTime.parse((String) t[3]))
                    .reference((String) t[4])
                    .description((String) t[5])
                    .channel(Transaction.TransactionChannel.valueOf((String) t[6]))
                    .status("PENDING")
                    .build());
        }
    }
}
