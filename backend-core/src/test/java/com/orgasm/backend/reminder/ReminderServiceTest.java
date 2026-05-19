package com.orgasm.backend.reminder;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock PlaylistRepository playlistRepository;
    @Mock NominationRepository nominationRepository;
    @Mock ContributorRepository contributorRepository;
    @InjectMocks ReminderService service;

    static Contributor contributor(long id, String email) {
        var c = new Contributor();
        c.setId(id);
        c.setName("Contributor " + id);
        c.setEmail(email);
        return c;
    }

    static Playlist openPlaylist(long id, Instant deadline) {
        var p = new Playlist();
        p.setId(id);
        p.setName("Playlist " + id);
        p.setStatus(PlaylistStatus.OPEN);
        p.setDeadline(deadline);
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
        var playlist = openPlaylist(10L, deadline);

        when(contributorRepository.findByEmailIsNotNull()).thenReturn(List.of(contributor));
        when(playlistRepository.findByStatusAndDeadlineBetween(eq(PlaylistStatus.OPEN), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of());

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
        var playlist = openPlaylist(10L, deadline);

        when(contributorRepository.findByEmailIsNotNull()).thenReturn(List.of(contributor));
        when(playlistRepository.findByStatusAndDeadlineBetween(eq(PlaylistStatus.OPEN), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of());

        var reminders = service.findReminders();

        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).daysUntilDeadline()).isEqualTo(2);
    }

    @Test
    void findReminders_returnsReminder_forDeadlineInThreeDays() {
        var contributor = contributor(1L, "a@example.com");
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(3).plusHours(12).toInstant();
        var playlist = openPlaylist(10L, deadline);

        when(contributorRepository.findByEmailIsNotNull()).thenReturn(List.of(contributor));
        when(playlistRepository.findByStatusAndDeadlineBetween(eq(PlaylistStatus.OPEN), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of());

        var reminders = service.findReminders();

        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).daysUntilDeadline()).isEqualTo(3);
    }

    @Test
    void findReminders_excludesContributor_withApprovedNomination() {
        var approved = contributor(1L, "approved@example.com");
        var pending = contributor(2L, "pending@example.com");
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(1).plusHours(12).toInstant();
        var playlist = openPlaylist(10L, deadline);

        when(contributorRepository.findByEmailIsNotNull()).thenReturn(List.of(approved, pending));
        when(playlistRepository.findByStatusAndDeadlineBetween(eq(PlaylistStatus.OPEN), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L)));

        var reminders = service.findReminders();

        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).contributorsToRemind()).containsExactly(pending);
    }

    @Test
    void findReminders_returnsEmpty_whenAllContributorsHaveApprovedNomination() {
        var contributor = contributor(1L, "a@example.com");
        var deadline = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).plusDays(1).plusHours(12).toInstant();
        var playlist = openPlaylist(10L, deadline);

        when(contributorRepository.findByEmailIsNotNull()).thenReturn(List.of(contributor));
        when(playlistRepository.findByStatusAndDeadlineBetween(eq(PlaylistStatus.OPEN), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to = inv.getArgument(2);
                    return deadline.isAfter(from) && deadline.isBefore(to) ? List.of(playlist) : List.of();
                });
        when(nominationRepository.findByPlaylist_IdAndStatus(10L, NominationStatus.APPROVED))
                .thenReturn(List.of(approvedNomination(1L)));

        assertThat(service.findReminders()).isEmpty();
    }

    @Test
    void findReminders_returnsEmpty_whenNoPlaylistsApproachingDeadline() {
        when(contributorRepository.findByEmailIsNotNull()).thenReturn(List.of(contributor(1L, "a@example.com")));
        when(playlistRepository.findByStatusAndDeadlineBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.findReminders()).isEmpty();
    }

    @Test
    void findReminders_returnsEmpty_whenNoContributorsWithEmail() {
        when(contributorRepository.findByEmailIsNotNull()).thenReturn(List.of());
        when(playlistRepository.findByStatusAndDeadlineBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.findReminders()).isEmpty();
    }
}
