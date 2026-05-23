package com.orgasm.backend.guessing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional("appTransactionManager")
public interface GuessRepository extends JpaRepository<Guess, Long> {

    List<Guess> findByPlaylist_Id(Long playlistId);

    @Modifying
    @Query("DELETE FROM Guess g WHERE g.playlist.id = :playlistId AND g.guesser.id = :guesserId")
    void deleteByPlaylistAndGuesser(@Param("playlistId") Long playlistId, @Param("guesserId") Long guesserId);
}
