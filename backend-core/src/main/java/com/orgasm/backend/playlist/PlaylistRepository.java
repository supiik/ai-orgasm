package com.orgasm.backend.playlist;

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

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Playlist> findByStatusAndDeadlineBetween(PlaylistStatus status, Instant from, Instant to);

    List<Playlist> findByStatusAndGuessingDeadlineBetween(PlaylistStatus status, Instant from, Instant to);

    Page<Playlist> findByLeadContributor_Id(Long leadContributorId, Pageable pageable);

    @Query(value = "SELECT DISTINCT n.playlist FROM Nomination n WHERE n.nominatedBy.id = :contributorId",
            countQuery = "SELECT COUNT(DISTINCT n.playlist) FROM Nomination n WHERE n.nominatedBy.id = :contributorId")
    Page<Playlist> findDistinctByNominations_NominatedBy_Id(@Param("contributorId") Long contributorId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Playlist p SET p.leadContributor = :contributor WHERE p.id = :id")
    void assignLeadContributor(@Param("id") Long id, @Param("contributor") com.orgasm.backend.contributor.Contributor contributor);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Playlist p SET p.deletedAt = :now WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id, @Param("now") Instant now);
}
