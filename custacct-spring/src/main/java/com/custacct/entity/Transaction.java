package com.custacct.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Transaction Record - Java equivalent of TRANCOPY.cpy (96-byte sequential record).
 *
 * COBOL → Java field mapping:
 *   TXN-CUST-ID    PIC 9(10)           → Long customerId
 *   TXN-TYPE       PIC X(2) (DP/WD/TR/PM) → TransactionType enum
 *   TXN-AMOUNT     PIC S9(9)V99 COMP-3 → BigDecimal amount
 *   TXN-DATE       PIC 9(8)            → LocalDate date
 *   TXN-TIME       PIC 9(6)            → LocalTime time
 *   TXN-REFERENCE  PIC X(16)           → String reference
 *   TXN-DESCRIPTION PIC X(40)          → String description
 *   TXN-CHANNEL    PIC X(3) (BRN/ATM/WEB/MOB) → TransactionChannel enum
 */
@Entity
@Table(name = "TRANSACTIONS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TXN-CUST-ID — foreign key to CUSTOMER-FILE primary key */
    @Column(name = "CUST_ID", nullable = false)
    private Long customerId;

    /**
     * TXN-TYPE PIC X(2)
     *   88 TXN-DEPOSIT    VALUE 'DP'
     *   88 TXN-WITHDRAWAL VALUE 'WD'
     *   88 TXN-TRANSFER   VALUE 'TR'
     *   88 TXN-PAYMENT    VALUE 'PM'
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "TXN_TYPE", length = 10, nullable = false)
    private TransactionType type;

    /** TXN-AMOUNT PIC S9(9)V99 COMP-3 */
    @Column(name = "AMOUNT", precision = 11, scale = 2, nullable = false)
    private BigDecimal amount;

    /** TXN-DATE PIC 9(8) format YYYYMMDD */
    @Column(name = "TXN_DATE")
    private LocalDate date;

    /** TXN-TIME PIC 9(6) format HHMMSS */
    @Column(name = "TXN_TIME")
    private LocalTime time;

    /** TXN-REFERENCE PIC X(16) */
    @Column(name = "REFERENCE", length = 16)
    private String reference;

    /** TXN-DESCRIPTION PIC X(40) */
    @Column(name = "DESCRIPTION", length = 40)
    private String description;

    /**
     * TXN-CHANNEL PIC X(3)
     *   88 TXN-BRANCH VALUE 'BRN'
     *   88 TXN-ATM    VALUE 'ATM'
     *   88 TXN-ONLINE VALUE 'WEB'
     *   88 TXN-MOBILE VALUE 'MOB'
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "CHANNEL", length = 10)
    private TransactionChannel channel;

    /** Processing status - set after batch run */
    @Column(name = "STATUS", length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** Error message if processing failed */
    @Column(name = "ERROR_MESSAGE", length = 80)
    private String errorMessage;

    // ----------------------------------------------------------------
    // COBOL 88-level type equivalents
    // ----------------------------------------------------------------

    public enum TransactionType {
        /** 88 TXN-DEPOSIT VALUE 'DP' */
        DP("DEPOSIT"),
        /** 88 TXN-WITHDRAWAL VALUE 'WD' */
        WD("WITHDRAWAL"),
        /** 88 TXN-TRANSFER VALUE 'TR' */
        TR("TRANSFER"),
        /** 88 TXN-PAYMENT VALUE 'PM' */
        PM("PAYMENT");

        private final String displayName;

        TransactionType(String displayName) { this.displayName = displayName; }

        public String getDisplayName() { return displayName; }

        public static TransactionType fromCode(String code) {
            for (TransactionType t : values()) {
                if (t.name().equalsIgnoreCase(code)) return t;
            }
            throw new IllegalArgumentException("Unknown transaction type: " + code);
        }
    }

    public enum TransactionChannel {
        /** 88 TXN-BRANCH VALUE 'BRN' */
        BRN("Branch"),
        /** 88 TXN-ATM VALUE 'ATM' */
        ATM("ATM"),
        /** 88 TXN-ONLINE VALUE 'WEB' */
        WEB("Online"),
        /** 88 TXN-MOBILE VALUE 'MOB' */
        MOB("Mobile");

        private final String displayName;

        TransactionChannel(String displayName) { this.displayName = displayName; }

        public String getDisplayName() { return displayName; }
    }
}
