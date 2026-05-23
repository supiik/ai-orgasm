package com.orgasm.backend.result;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.guessing.Guess;
import com.orgasm.backend.guessing.GuessRepository;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.song.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuessingResultServiceTest {

    @Mock NominationRepository nominationRepository;
    @Mock GuessRepository guessRepository;
    @InjectMocks GuessingResultService service;

    static Contributor contributor(long id) {
        var c = new Contributor();
        c.setId(id);
        c.setName("Contributor " + id);
        c.setEmail(id + "@example.com");
        return c;
    }

    static Song song(long id) {
        var s = new Song();
        s.setId(id);
        s.setName("Song " + id);
        s.setArtist("Artist " + id);
        return s;
    }

    static Nomination nomination(long id, Contributor nominatedBy, Song song) {
        var n = new Nomination();
        n.setId(id);
        n.setNominatedBy(nominatedBy);
        n.setSong(song);
        n.setStatus(NominationStatus.APPROVED);
        return n;
    }

    static Guess guess(long id, Nomination nomination, Contributor guesser, Contributor guessedContributor) {
        var g = new Guess();
        g.setId(id);
        g.setNomination(nomination);
        g.setGuesser(guesser);
        g.setGuessedContributor(guessedContributor);
        return g;
    }

    @Test
    void getResults_returnsNominationWithCorrectGuess() {
        var nominator = contributor(1L);
        var guesser = contributor(2L);
        var song = song(10L);
        var nomination = nomination(100L, nominator, song);
        var guess = guess(200L, nomination, guesser, nominator);

        when(nominationRepository.findByPlaylist_IdAndStatus(5L, NominationStatus.APPROVED))
                .thenReturn(List.of(nomination));
        when(guessRepository.findByPlaylist_Id(5L)).thenReturn(List.of(guess));

        var results = service.getResults(5L);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.nomination()).isEqualTo(nomination);
        assertThat(result.guesses()).hasSize(1);
        assertThat(result.guesses().get(0).guesser()).isEqualTo(guesser);
        assertThat(result.guesses().get(0).guessedContributor()).isEqualTo(nominator);
        assertThat(result.guesses().get(0).correct()).isTrue();
    }

    @Test
    void getResults_marksIncorrectGuess() {
        var nominator = contributor(1L);
        var guesser = contributor(2L);
        var wrongContributor = contributor(3L);
        var song = song(10L);
        var nomination = nomination(100L, nominator, song);
        var guess = guess(200L, nomination, guesser, wrongContributor);

        when(nominationRepository.findByPlaylist_IdAndStatus(5L, NominationStatus.APPROVED))
                .thenReturn(List.of(nomination));
        when(guessRepository.findByPlaylist_Id(5L)).thenReturn(List.of(guess));

        var results = service.getResults(5L);

        assertThat(results.get(0).guesses().get(0).correct()).isFalse();
    }

    @Test
    void getResults_returnsEmptyGuessesWhenNobodyGuessed() {
        var nominator = contributor(1L);
        var nomination = nomination(100L, nominator, song(10L));

        when(nominationRepository.findByPlaylist_IdAndStatus(5L, NominationStatus.APPROVED))
                .thenReturn(List.of(nomination));
        when(guessRepository.findByPlaylist_Id(5L)).thenReturn(List.of());

        var results = service.getResults(5L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).guesses()).isEmpty();
    }

    @Test
    void getResults_returnsEmpty_whenNoApprovedNominations() {
        when(nominationRepository.findByPlaylist_IdAndStatus(5L, NominationStatus.APPROVED))
                .thenReturn(List.of());
        when(guessRepository.findByPlaylist_Id(5L)).thenReturn(List.of());

        assertThat(service.getResults(5L)).isEmpty();
    }
}
