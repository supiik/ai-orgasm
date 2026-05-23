package com.orgasm.backend.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional("appTransactionManager")
public interface PlaylistRankingRepository extends JpaRepository<PlaylistRanking, Long> {

    List<PlaylistRanking> findByPlaylist_IdOrderByRankPositionAsc(Long playlistId);

    @Query("SELECT r FROM PlaylistRanking r JOIN FETCH r.playlist JOIN FETCH r.contributor ORDER BY r.playlist.id, r.rankPosition")
    List<PlaylistRanking> findAllWithDetails();
}
