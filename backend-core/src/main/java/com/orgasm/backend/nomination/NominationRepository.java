package com.orgasm.backend.nomination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional("appTransactionManager")
public interface NominationRepository extends JpaRepository<Nomination, Long> {

    Page<Nomination> findByPlaylistId(Long playlistId, Pageable pageable);

    boolean existsByPlaylistIdAndSongId(Long playlistId, Long songId);
}
