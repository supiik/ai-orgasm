package com.etactics.proxima.service.backend.playlist;

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
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Page<Playlist> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Playlist> findByStatusAndDeadlineBetween(PlaylistStatus status, Instant from, Instant to);

    Page<Playlist> findByLeadContributor_Id(Long leadContributorId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Playlist p SET p.leadContributor = :contributor WHERE p.id = :id")
    void assignLeadContributor(@Param("id") Long id, @Param("contributor") com.etactics.proxima.service.backend.contributor.Contributor contributor);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Playlist p SET p.deletedAt = :now WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id, @Param("now") Instant now);
}
