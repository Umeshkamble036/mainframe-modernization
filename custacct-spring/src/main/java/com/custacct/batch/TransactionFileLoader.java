package com.custacct.batch;

import com.custacct.entity.Transaction;
import com.custacct.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Flat File Transaction Loader
 *
 * Parses the DAILY.TRANSACTIONS.RAW.dat format into Transaction entities.
 * This replaces the JCL DD statement:
 *   //TRANFILE  DD  DSN=DAILY.TRANSACTIONS.RAW,DISP=SHR
 *
 * COBOL TRANCOPY.cpy record layout (96 bytes total):
 *   Pos  1-10  : TXN-CUST-ID     PIC 9(10)       — left-padded with zeros
 *   Pos 11-12  : TXN-TYPE        PIC X(2)         — DP/WD/TR/PM
 *   Pos 13-18  : TXN-AMOUNT      PIC S9(9)V99     — display decimal (no decimal point)
 *                                                    '{' prefix = positive overpunch
 *   Pos 19-26  : TXN-DATE        PIC 9(8)         — YYYYMMDD
 *   Pos 27-32  : TXN-TIME        PIC 9(6)         — HHMMSS
 *   Pos 33-48  : TXN-REFERENCE   PIC X(16)
 *   Pos 49-88  : TXN-DESCRIPTION PIC X(40)
 *   Pos 89-91  : TXN-CHANNEL     PIC X(3)         — BRN/ATM/WEB/MOB
 *   Pos 92-96  : TXN-FILLER      PIC X(3) (spaces)
 *
 * Note: The '{' character is an EBCDIC/COBOL overpunch sign indicator.
 *   In packed decimal dumps: '{' = positive 0, 'A'=+1 ... 'I'=+9.
 *   Here we strip it and treat the remainder as a 9(9)V99 display number.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionFileLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

    private final TransactionRepository transactionRepository;

    /**
     * Load transactions from a flat file into the database as PENDING.
     * Call this before running the batch job to seed the transaction queue.
     *
     * @param filePath  path to DAILY.TRANSACTIONS.RAW.dat
     * @return number of records loaded
     */
    public int loadFromFile(String filePath) throws IOException {
        List<Transaction> loaded = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;

                try {
                    Transaction txn = parseLine(line, lineNum);
                    loaded.add(txn);
                } catch (Exception e) {
                    log.warn("Line {}: Could not parse transaction: {} — {}", lineNum, line, e.getMessage());
                }
            }
        }

        transactionRepository.saveAll(loaded);
        log.info("Loaded {} transactions from file: {}", loaded.size(), filePath);
        return loaded.size();
    }

    /**
     * Parse a single 96-character transaction record.
     * Maps directly to TRANCOPY.cpy field layout.
     */
    Transaction parseLine(String line, int lineNum) {
        if (line.length() < 32) {
            throw new IllegalArgumentException("Line too short: " + line.length() + " chars");
        }

        // Pos 1-10: TXN-CUST-ID PIC 9(10)
        long custId = Long.parseLong(line.substring(0, 10).trim());

        // Pos 11-12: TXN-TYPE PIC X(2)
        String typeCode = line.substring(10, 12).trim();
        Transaction.TransactionType type = Transaction.TransactionType.fromCode(typeCode);

        // Pos 13-18: TXN-AMOUNT (6 chars in display, stripped of overpunch '{')
        //   The '{' is an EBCDIC positive-zone overpunch on the final digit.
        //   Strip it, parse as 9(9)V99 (implied 2 decimal places)
        String amountStr = line.substring(12, 18).replace("{", "").replace("}", "").trim();
        BigDecimal amount = parsePackedAmount(amountStr);

        // Pos 19-26: TXN-DATE PIC 9(8) YYYYMMDD
        String dateStr = line.substring(18, 26).trim();
        LocalDate date = LocalDate.parse(dateStr, DATE_FMT);

        // Pos 27-32: TXN-TIME PIC 9(6) HHMMSS
        String timeStr = line.substring(26, 32).trim();
        LocalTime time = LocalTime.parse(timeStr, TIME_FMT);

        // Pos 33-48: TXN-REFERENCE PIC X(16)
        String reference = line.length() >= 48 ? line.substring(32, 48).trim() : "";

        // Pos 49-88: TXN-DESCRIPTION PIC X(40)
        String description = line.length() >= 88 ? line.substring(48, 88).trim() : "";

        // Pos 89-91: TXN-CHANNEL PIC X(3)
        Transaction.TransactionChannel channel = null;
        if (line.length() >= 91) {
            String channelCode = line.substring(88, 91).trim();
            try {
                channel = Transaction.TransactionChannel.valueOf(channelCode);
            } catch (IllegalArgumentException e) {
                log.debug("Unknown channel code '{}' on line {}", channelCode, lineNum);
            }
        }

        return Transaction.builder()
                .customerId(custId)
                .type(type)
                .amount(amount)
                .date(date)
                .time(time)
                .reference(reference)
                .description(description)
                .channel(channel)
                .status("PENDING")
                .build();
    }

    /**
     * Parse a COBOL display-numeric amount string (implied 2 decimal places).
     * e.g. "000100" → 1.00, "010000" → 100.00
     */
    BigDecimal parsePackedAmount(String numStr) {
        if (numStr == null || numStr.isBlank()) return BigDecimal.ZERO;
        // Remove any remaining overpunch characters
        String cleaned = numStr.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) return BigDecimal.ZERO;
        long raw = Long.parseLong(cleaned);
        return BigDecimal.valueOf(raw, 2); // implied 2 decimal places (V99)
    }
}
