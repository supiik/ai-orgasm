package com.pi2.anchor.backend.sample;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Transactional("appTransactionManager")
public interface SampleRepository extends JpaRepository<Sample, Long> {

    Page<Sample> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Sample> findByStatus(SampleStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Sample s SET s.deletedAt = :now WHERE s.id = :id AND s.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id, @Param("now") Instant now);
}
