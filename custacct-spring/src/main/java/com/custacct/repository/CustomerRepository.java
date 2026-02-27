package com.custacct.repository;

import com.custacct.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Customer Repository - replaces VSAM KSDS file I/O.
 *
 * COBOL I/O operations → Spring Data JPA equivalents:
 *   READ CUSTOMER-FILE (INVALID KEY)   → findById() → Optional.empty()
 *   READ CUSTOMER-FILE (NOT INVALID)   → findById() → Optional.of(customer)
 *   REWRITE CUSTOMER-RECORD            → save(customer)
 *   WRITE CUSTOMER-RECORD              → save(customer) [for new records]
 *   DELETE CUSTOMER-RECORD             → deleteById(id)
 *
 * VSAM ALTERNATE KEY (CUST-EMAIL WITH DUPLICATES) → findByEmail()
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Alternate key lookup by email.
     * COBOL: READ CUSTOMER-FILE KEY IS CUST-EMAIL
     */
    List<Customer> findByEmail(String email);

    /**
     * Find by status — useful for reports and admin queries.
     */
    List<Customer> findByStatus(Customer.CustomerStatus status);

    /**
     * Branch-based query.
     */
    List<Customer> findByBranchCode(Integer branchCode);
}
