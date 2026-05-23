package com.orgasm.backend.guessing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional("appTransactionManager")
public interface GuessSubmissionRepository extends JpaRepository<GuessSubmission, Long> {

    boolean existsByPlaylist_IdAndContributor_Id(Long playlistId, Long contributorId);

    @Query("SELECT gs.contributor.id FROM GuessSubmission gs WHERE gs.playlist.id = :playlistId")
    List<Long> findContributorIdsByPlaylistId(@Param("playlistId") Long playlistId);
}
