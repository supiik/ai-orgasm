package com.orgasm.backend.result;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.guessing.Guess;
import com.orgasm.backend.guessing.GuessRepository;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(value = "appTransactionManager", readOnly = true)
@RequiredArgsConstructor
public class GuessingResultService {

    private final NominationRepository nominationRepository;
    private final GuessRepository guessRepository;

    public record GuessDetail(Contributor guesser, Contributor guessedContributor, boolean correct) {}
    public record NominationResult(Nomination nomination, List<GuessDetail> guesses) {}

    public List<NominationResult> getResults(Long playlistId) {
        List<Nomination> approved = nominationRepository.findByPlaylist_IdAndStatus(playlistId, NominationStatus.APPROVED);
        List<Guess> allGuesses = guessRepository.findByPlaylist_Id(playlistId);

        return approved.stream().map(nomination -> {
            List<GuessDetail> details = allGuesses.stream()
                    .filter(g -> g.getNomination().getId().equals(nomination.getId()))
                    .map(g -> new GuessDetail(
                            g.getGuesser(),
                            g.getGuessedContributor(),
                            g.getGuessedContributor().getId().equals(nomination.getNominatedBy().getId())))
                    .toList();
            return new NominationResult(nomination, details);
        }).toList();
    }
}
