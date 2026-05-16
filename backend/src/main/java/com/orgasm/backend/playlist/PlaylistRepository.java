package com.orgasm.backend.playlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional("appTransactionManager")
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
}
