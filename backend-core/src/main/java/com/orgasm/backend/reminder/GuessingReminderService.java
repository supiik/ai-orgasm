package com.orgasm.backend.reminder;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import com.orgasm.backend.guessing.GuessSubmissionRepository;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(value = "appTransactionManager", readOnly = true)
@RequiredArgsConstructor
public class GuessingReminderService {

    private final PlaylistRepository playlistRepository;
    private final NominationRepository nominationRepository;
    private final ContributorRepository contributorRepository;
    private final GuessSubmissionRepository guessSubmissionRepository;

    public record GuessingReminder(Playlist playlist, List<Contributor> contributorsToRemind, int daysUntilDeadline) {}

    public List<GuessingReminder> findReminders() {
        ZonedDateTime startOfToday = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        List<GuessingReminder> reminders = new ArrayList<>();

        for (int days : List.of(1, 2, 3)) {
            Instant windowStart = startOfToday.plusDays(days).toInstant();
            Instant windowEnd = startOfToday.plusDays(days + 1).toInstant();
            List<Playlist> playlists = playlistRepository
                    .findByStatusAndGuessingDeadlineBetween(PlaylistStatus.GUESSING, windowStart, windowEnd);

            for (Playlist playlist : playlists) {
                Set<Long> approvedNominatorIds = nominationRepository
                        .findByPlaylist_IdAndStatus(playlist.getId(), NominationStatus.APPROVED)
                        .stream()
                        .map(n -> n.getNominatedBy().getId())
                        .collect(Collectors.toSet());

                Set<Long> submittedIds = guessSubmissionRepository
                        .findContributorIdsByPlaylistId(playlist.getId())
                        .stream()
                        .collect(Collectors.toSet());

                List<Contributor> toRemind = approvedNominatorIds.stream()
                        .filter(id -> !submittedIds.contains(id))
                        .map(id -> contributorRepository.findById(id).orElse(null))
                        .filter(c -> c != null && c.getEmail() != null)
                        .collect(Collectors.toList());

                if (!toRemind.isEmpty()) {
                    reminders.add(new GuessingReminder(playlist, toRemind, days));
                }
            }
        }

        return reminders;
    }
}
