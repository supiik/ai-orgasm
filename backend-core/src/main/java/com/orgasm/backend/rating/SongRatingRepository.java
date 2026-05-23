package com.orgasm.backend.rating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional("appTransactionManager")
public interface SongRatingRepository extends JpaRepository<SongRating, Long> {

    @Query("SELECT r FROM SongRating r JOIN FETCH r.contributor JOIN FETCH r.nomination WHERE r.playlist.id = :playlistId")
    List<SongRating> findByPlaylistWithDetails(@Param("playlistId") Long playlistId);

    @Modifying
    @Query("DELETE FROM SongRating r WHERE r.playlist.id = :playlistId AND r.contributor.id = :contributorId")
    void deleteByPlaylistAndContributor(@Param("playlistId") Long playlistId, @Param("contributorId") Long contributorId);
}
