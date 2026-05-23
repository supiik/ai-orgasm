package com.orgasm.backend.reminder;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import com.orgasm.backend.guessing.GuessSubmissionRepository;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuessingReminderServiceTest {

    @Mock PlaylistRepository playlistRepository;
    @Mock NominationRepository nominationRepository;
    @Mock ContributorRepository contributorRepository;
    @Mock GuessSubmissionRepository guessSubmissionRepository;
    @InjectMocks GuessingReminderService service;

    static Contributor contributor(long id, String email) {
        var c = new Contributor();
        c.setId(id);
        c.setName("Contributor " + id);
        c.setEmail(email);
        return c;
    }

    static Playlist guessingPlaylist(long id, Instant guessingDeadline) {
        var p = new Playlist();
        p.setId(id);
        p.setName("Playlist " + id);
        p.setStatus(PlaylistStatus.GUESSING);
        p.setGuessingDeadline(guessingDeadline);
        return p;
    }

    static Nomination approvedNomination(long nominatedById) {
        var c = new Contributor();
        c.setId(nominatedById);
        var n = new Nomination();
        n.setId(99L);
        n.setNominatedBy(c);
        n.setStatus(NominationStatus.APPROVED);
        return n;
    }

    @Test
    void findReminders_returnsReminder_forPlaylistDeadlineInOneDay() {
        var contributor = contributor(1L, "a@example.com");
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(1).plusHours(12).toInstant();
        var playlist = guessingPlaylist(10L, deadline);

        when(playlistRepository.findByStatusAndGuessingDeadlineBetween(eq(PlaylistStatus.GUESSING), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L)));
        when(guessSubmissionRepository.findContributorIdsByPlaylistId(10L)).thenReturn(List.of());
        when(contributorRepository.findById(1L)).thenReturn(Optional.of(contributor));

        var reminders = service.findReminders();

        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).daysUntilDeadline()).isEqualTo(1);
        assertThat(reminders.get(0).playlist()).isEqualTo(playlist);
        assertThat(reminders.get(0).contributorsToRemind()).containsExactly(contributor);
    }

    @Test
    void findReminders_returnsReminder_forDeadlineInTwoDays() {
        var contributor = contributor(1L, "a@example.com");
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(2).plusHours(12).toInstant();
        var playlist = guessingPlaylist(10L, deadline);

        when(playlistRepository.findByStatusAndGuessingDeadlineBetween(eq(PlaylistStatus.GUESSING), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L)));
        when(guessSubmissionRepository.findContributorIdsByPlaylistId(10L)).thenReturn(List.of());
        when(contributorRepository.findById(1L)).thenReturn(Optional.of(contributor));

        var reminders = service.findReminders();

        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).daysUntilDeadline()).isEqualTo(2);
    }

    @Test
    void findReminders_returnsReminder_forDeadlineInThreeDays() {
        var contributor = contributor(1L, "a@example.com");
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(3).plusHours(12).toInstant();
        var playlist = guessingPlaylist(10L, deadline);

        when(playlistRepository.findByStatusAndGuessingDeadlineBetween(eq(PlaylistStatus.GUESSING), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L)));
        when(guessSubmissionRepository.findContributorIdsByPlaylistId(10L)).thenReturn(List.of());
        when(contributorRepository.findById(1L)).thenReturn(Optional.of(contributor));

        var reminders = service.findReminders();

        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).daysUntilDeadline()).isEqualTo(3);
    }

    @Test
    void findReminders_excludesContributor_whoAlreadySubmittedGuesses() {
        var submitted = contributor(1L, "submitted@example.com");
        var notSubmitted = contributor(2L, "pending@example.com");
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(1).plusHours(12).toInstant();
        var playlist = guessingPlaylist(10L, deadline);

        when(playlistRepository.findByStatusAndGuessingDeadlineBetween(eq(PlaylistStatus.GUESSING), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L), approvedNomination(2L)));
        when(guessSubmissionRepository.findContributorIdsByPlaylistId(10L)).thenReturn(List.of(1L));
        when(contributorRepository.findById(2L)).thenReturn(Optional.of(notSubmitted));

        var reminders = service.findReminders();

        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).contributorsToRemind()).containsExactly(notSubmitted);
    }

    @Test
    void findReminders_returnsEmpty_whenAllContributorsSubmitted() {
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(1).plusHours(12).toInstant();
        var playlist = guessingPlaylist(10L, deadline);

        when(playlistRepository.findByStatusAndGuessingDeadlineBetween(eq(PlaylistStatus.GUESSING), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L)));
        when(guessSubmissionRepository.findContributorIdsByPlaylistId(10L)).thenReturn(List.of(1L));

        assertThat(service.findReminders()).isEmpty();
    }

    @Test
    void findReminders_returnsEmpty_whenNoPlaylistsApproachingDeadline() {
        when(playlistRepository.findByStatusAndGuessingDeadlineBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.findReminders()).isEmpty();
    }

    @Test
    void findReminders_excludesContributor_withoutEmail() {
        var noEmail = contributor(1L, null);
        noEmail.setEmail(null);
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(1).plusHours(12).toInstant();
        var playlist = guessingPlaylist(10L, deadline);

        when(playlistRepository.findByStatusAndGuessingDeadlineBetween(eq(PlaylistStatus.GUESSING), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L)));
        when(guessSubmissionRepository.findContributorIdsByPlaylistId(10L)).thenReturn(List.of());
        when(contributorRepository.findById(1L)).thenReturn(Optional.of(noEmail));

        assertThat(service.findReminders()).isEmpty();
    }
}
