package com.orgasm.backend.playlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Transactional("appTransactionManager")
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    @Modifying
    @Query("UPDATE Playlist p SET p.deletedAt = :now WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id, @Param("now") Instant now);
}
