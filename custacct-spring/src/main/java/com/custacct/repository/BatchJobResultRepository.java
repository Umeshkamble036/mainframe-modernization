package com.custacct.repository;

import com.custacct.entity.BatchJobResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BatchJobResultRepository extends JpaRepository<BatchJobResult, Long> {

    /** Get the most recent batch run result */
    Optional<BatchJobResult> findTopByOrderByRunDateTimeDesc();
}
