package com.custacct.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Customer Master Record - Java equivalent of CUSTCOPY.cpy (VSAM KSDS record).
 *
 * COBOL → Java field mapping:
 *   CUST-ID            PIC 9(10)          → Long id (primary key)
 *   CUST-LAST-NAME     PIC X(20)          → String lastName
 *   CUST-FIRST-NAME    PIC X(15)          → String firstName
 *   CUST-STREET        PIC X(30)          → String street
 *   CUST-CITY          PIC X(20)          → String city
 *   CUST-STATE         PIC X(2)           → String state
 *   CUST-ZIP           PIC 9(5)           → String zipCode
 *   CUST-PHONE         PIC 9(10)          → String phone
 *   CUST-EMAIL         PIC X(40)          → String email (alternate VSAM key)
 *   CUST-ACCOUNT-BAL   PIC S9(11)V99 COMP-3 → BigDecimal accountBalance
 *   CUST-CREDIT-LIMIT  PIC S9(9)V99  COMP-3 → BigDecimal creditLimit
 *   CUST-MIN-BALANCE   PIC S9(7)V99  COMP-3 → BigDecimal minBalance
 *   CUST-STATUS        PIC X(1) (A/I/S/C) → CustomerStatus enum
 *   CUST-OPEN-DATE     PIC 9(8)           → LocalDate openDate
 *   CUST-LAST-TXN-DATE PIC 9(8)           → LocalDate lastTransactionDate
 *   CUST-TXN-COUNT     PIC 9(7) COMP-3    → int transactionCount
 *   CUST-BRANCH-CODE   PIC 9(4)           → Integer branchCode
 */
@Entity
@Table(name = "CUSTOMER_MASTER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    /** CUST-ID — VSAM KSDS primary key */
    @Id
    @Column(name = "CUST_ID", nullable = false)
    private Long id;

    /** CUST-LAST-NAME */
    @Column(name = "LAST_NAME", length = 20, nullable = false)
    @NotBlank
    private String lastName;

    /** CUST-FIRST-NAME */
    @Column(name = "FIRST_NAME", length = 15, nullable = false)
    @NotBlank
    private String firstName;

    /** CUST-STREET */
    @Column(name = "STREET", length = 30)
    private String street;

    /** CUST-CITY */
    @Column(name = "CITY", length = 20)
    private String city;

    /** CUST-STATE */
    @Column(name = "STATE", length = 2)
    private String state;

    /** CUST-ZIP */
    @Column(name = "ZIP_CODE", length = 5)
    private String zipCode;

    /** CUST-PHONE */
    @Column(name = "PHONE", length = 10)
    private String phone;

    /** CUST-EMAIL — alternate VSAM KSDS key (duplicates allowed in COBOL) */
    @Column(name = "EMAIL", length = 40)
    private String email;

    /** CUST-ACCOUNT-BAL PIC S9(11)V99 COMP-3 */
    @Column(name = "ACCOUNT_BALANCE", precision = 13, scale = 2, nullable = false)
    @DecimalMin(value = "-9999999999.99")
    private BigDecimal accountBalance;

    /** CUST-CREDIT-LIMIT PIC S9(9)V99 COMP-3 */
    @Column(name = "CREDIT_LIMIT", precision = 11, scale = 2)
    private BigDecimal creditLimit;

    /** CUST-MIN-BALANCE PIC S9(7)V99 COMP-3 */
    @Column(name = "MIN_BALANCE", precision = 9, scale = 2)
    private BigDecimal minBalance;

    /**
     * CUST-STATUS PIC X(1)
     *   88 CUST-ACTIVE    VALUE 'A'
     *   88 CUST-INACTIVE  VALUE 'I'
     *   88 CUST-SUSPENDED VALUE 'S'
     *   88 CUST-CLOSED    VALUE 'C'
     */
    @Convert(converter = CustomerStatusConverter.class)
    @Column(name = "STATUS", length = 1, nullable = false)
    private CustomerStatus status;

    /** CUST-OPEN-DATE PIC 9(8) format YYYYMMDD */
    @Column(name = "OPEN_DATE")
    private LocalDate openDate;

    /** CUST-LAST-TXN-DATE PIC 9(8) format YYYYMMDD */
    @Column(name = "LAST_TXN_DATE")
    private LocalDate lastTransactionDate;

    /** CUST-TXN-COUNT PIC 9(7) COMP-3 */
    @Column(name = "TXN_COUNT")
    private int transactionCount;

    /** CUST-BRANCH-CODE PIC 9(4) */
    @Column(name = "BRANCH_CODE")
    private Integer branchCode;

    // ================================================================
    // Explicit Getters & Setters (work around Lombok annotation processing)
    // ================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public BigDecimal getAccountBalance() { return accountBalance; }
    public void setAccountBalance(BigDecimal accountBalance) { this.accountBalance = accountBalance; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public BigDecimal getMinBalance() { return minBalance; }
    public void setMinBalance(BigDecimal minBalance) { this.minBalance = minBalance; }

    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }

    public LocalDate getOpenDate() { return openDate; }
    public void setOpenDate(LocalDate openDate) { this.openDate = openDate; }

    public LocalDate getLastTransactionDate() { return lastTransactionDate; }
    public void setLastTransactionDate(LocalDate lastTransactionDate) { this.lastTransactionDate = lastTransactionDate; }

    public int getTransactionCount() { return transactionCount; }
    public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }

    public Integer getBranchCode() { return branchCode; }
    public void setBranchCode(Integer branchCode) { this.branchCode = branchCode; }

    // ----------------------------------------------------------------
    // Convenience / business logic methods (replaces COBOL 88-level conditions)
    // ----------------------------------------------------------------

    /** Equivalent to: 88 CUST-ACTIVE VALUE 'A' */
    public boolean isActive() {
        return CustomerStatus.ACTIVE == this.status;
    }

    public String getFullName() {
        return firstName.trim() + " " + lastName.trim();
    }

    /**
     * Status codes matching COBOL 88-level condition names.
     */
    public enum CustomerStatus {
        /** 88 CUST-ACTIVE */
        ACTIVE("A"),
        /** 88 CUST-INACTIVE */
        INACTIVE("I"),
        /** 88 CUST-SUSPENDED */
        SUSPENDED("S"),
        /** 88 CUST-CLOSED */
        CLOSED("C");

        private final String code;

        CustomerStatus(String code) { this.code = code; }

        public String getCode() { return code; }

        public static CustomerStatus fromCode(String code) {
            for (CustomerStatus s : values()) {
                if (s.code.equalsIgnoreCase(code)) return s;
            }
            throw new IllegalArgumentException("Unknown status code: " + code);
        }
    }
}
