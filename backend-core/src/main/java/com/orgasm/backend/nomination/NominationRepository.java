package com.orgasm.backend.nomination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional("appTransactionManager")
public interface NominationRepository extends JpaRepository<Nomination, Long> {

    Page<Nomination> findByPlaylist_Id(Long playlistId, Pageable pageable);

    boolean existsByPlaylist_IdAndSong_Id(Long playlistId, Long songId);

    List<Nomination> findByPlaylist_IdAndStatus(Long playlistId, NominationStatus status);

    @Modifying
    @Query("UPDATE Nomination n SET n.status = com.orgasm.backend.nomination.NominationStatus.DECLINED WHERE n.playlist.id = :playlistId AND n.status = com.orgasm.backend.nomination.NominationStatus.PENDING")
    int declinePendingByPlaylistId(Long playlistId);
}
