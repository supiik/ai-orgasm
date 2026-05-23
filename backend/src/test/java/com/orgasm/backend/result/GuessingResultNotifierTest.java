package com.orgasm.backend.result;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.email.EmailMessage;
import com.orgasm.backend.email.EmailService;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.result.GuessingResultService.GuessDetail;
import com.orgasm.backend.result.GuessingResultService.NominationResult;
import com.orgasm.backend.song.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuessingResultNotifierTest {

    @Mock GuessingResultService guessingResultService;
    @Mock EmailService emailService;
    @InjectMocks GuessingResultNotifier notifier;

    static Contributor contributor(long id, String name, String email) {
        var c = new Contributor();
        c.setId(id);
        c.setName(name);
        c.setEmail(email);
        return c;
    }

    static NominationResult buildResult(Contributor nominator, List<GuessDetail> guesses) {
        var playlist = new Playlist();
        playlist.setId(1L);
        playlist.setName("Summer Mix");

        var song = new Song();
        song.setId(10L);
        song.setName("Creep");
        song.setArtist("Radiohead");

        var nomination = new Nomination();
        nomination.setId(100L);
        nomination.setPlaylist(playlist);
        nomination.setSong(song);
        nomination.setNominatedBy(nominator);

        return new NominationResult(nomination, guesses);
    }

    @Test
    void notifyPublish_sendsEmailToNominator() {
        var nominator = contributor(1L, "Thom", "thom@example.com");
        var guesser = contributor(2L, "Jonny", "jonny@example.com");
        var guessDetail = new GuessDetail(guesser, nominator, true);
        var result = buildResult(nominator, List.of(guessDetail));

        when(guessingResultService.getResults(anyLong())).thenReturn(List.of(result));

        notifier.notifyPublish("1000000000000000");

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());

        var msg = captor.getValue();
        assertThat(msg.to()).isEqualTo("thom@example.com");
        assertThat(msg.subject()).contains("Summer Mix");
        assertThat(msg.body()).contains("Thom").contains("Creep").contains("Radiohead").contains("Jonny");
    }

    @Test
    void notifyPublish_marksCorrectGuessWithCheckmark() {
        var nominator = contributor(1L, "Thom", "thom@example.com");
        var guesser = contributor(2L, "Jonny", "jonny@example.com");
        var result = buildResult(nominator, List.of(new GuessDetail(guesser, nominator, true)));

        when(guessingResultService.getResults(anyLong())).thenReturn(List.of(result));

        notifier.notifyPublish("1000000000000000");

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().body()).contains("✓");
    }

    @Test
    void notifyPublish_marksIncorrectGuessWithCross() {
        var nominator = contributor(1L, "Thom", "thom@example.com");
        var guesser = contributor(2L, "Jonny", "jonny@example.com");
        var other = contributor(3L, "Colin", "colin@example.com");
        var result = buildResult(nominator, List.of(new GuessDetail(guesser, other, false)));

        when(guessingResultService.getResults(anyLong())).thenReturn(List.of(result));

        notifier.notifyPublish("1000000000000000");

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().body()).contains("✗");
    }

    @Test
    void notifyPublish_skipsNominatorWithoutEmail() {
        var nominator = contributor(1L, "Thom", null);
        var result = buildResult(nominator, List.of());

        when(guessingResultService.getResults(anyLong())).thenReturn(List.of(result));

        notifier.notifyPublish("1000000000000000");

        verifyNoInteractions(emailService);
    }

    @Test
    void notifyPublish_continuesSending_whenOneEmailFails() {
        var nominator1 = contributor(1L, "Thom", "thom@example.com");
        var nominator2 = contributor(2L, "Jonny", "jonny@example.com");

        when(guessingResultService.getResults(anyLong()))
                .thenReturn(List.of(buildResult(nominator1, List.of()), buildResult(nominator2, List.of())));
        doThrow(new MailSendException("SMTP down"))
                .when(emailService).send(argThat(m -> m.to().equals("thom@example.com")));

        notifier.notifyPublish("1000000000000000");

        verify(emailService, times(2)).send(any());
    }

    @Test
    void notifyPublish_doesNothing_whenNoResults() {
        when(guessingResultService.getResults(anyLong())).thenReturn(List.of());

        notifier.notifyPublish("1000000000000000");

        verifyNoInteractions(emailService);
    }
}
