package com.orgasm.backend.contributor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Transactional("appTransactionManager")
public interface ContributorRepository extends JpaRepository<Contributor, Long> {

    Page<Contributor> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Contributor> findByEmailIsNotNull();

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Contributor c SET c.deletedAt = :now WHERE c.id = :id AND c.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id, @Param("now") Instant now);
}
