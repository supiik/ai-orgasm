package com.orgasm.backend.song;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Transactional("appTransactionManager")
public interface SongRepository extends JpaRepository<Song, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Song s SET s.deletedAt = :now WHERE s.id = :id AND s.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id, @Param("now") Instant now);
}
