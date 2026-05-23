package com.orgasm.backend.result;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.email.EmailMessage;
import com.orgasm.backend.email.EmailService;
import com.orgasm.backend.result.GuessingResultService.GuessDetail;
import com.orgasm.backend.result.GuessingResultService.NominationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class GuessingResultNotifier {

    private final GuessingResultService guessingResultService;
    private final EmailService emailService;

    public void notifyPublish(String playlistId) {
        List<NominationResult> results = guessingResultService.getResults(IdGenerator.parse(playlistId));
        for (NominationResult result : results) {
            Contributor nominator = result.nomination().getNominatedBy();
            if (nominator.getEmail() == null || nominator.getEmail().isBlank()) continue;
            try {
                emailService.send(buildMessage(result));
            } catch (Exception e) {
                log.warn("Failed to send result notification to {}: {}", nominator.getEmail(), e.getMessage());
            }
        }
    }

    private EmailMessage buildMessage(NominationResult result) {
        var nomination = result.nomination();
        var nominator = nomination.getNominatedBy();
        var song = nomination.getSong();

        StringBuilder guessLines = new StringBuilder();
        if (result.guesses().isEmpty()) {
            guessLines.append("  No one guessed your song.\n");
        } else {
            for (GuessDetail g : result.guesses()) {
                guessLines.append("  - ")
                        .append(g.guesser().getName())
                        .append(" guessed ")
                        .append(g.guessedContributor().getName())
                        .append(g.correct() ? " ✓" : " ✗")
                        .append("\n");
            }
        }

        String subject = String.format("Results: who guessed your song in \"%s\"?", nomination.getPlaylist().getName());
        String body = String.format("""
                Hi %s,

                The playlist "%s" has been published!

                Your song "%s" by %s was guessed as follows:
                %s
                """,
                nominator.getName(),
                nomination.getPlaylist().getName(),
                song.getName(),
                song.getArtist(),
                guessLines);

        return new EmailMessage(nominator.getEmail(), subject, body);
    }
}
