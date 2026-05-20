package com.etactics.proxima.service.backend.reminder;

import com.etactics.proxima.service.backend.contributor.Contributor;
import com.etactics.proxima.service.backend.contributor.ContributorRepository;
import com.etactics.proxima.service.backend.nomination.Nomination;
import com.etactics.proxima.service.backend.nomination.NominationRepository;
import com.etactics.proxima.service.backend.nomination.NominationStatus;
import com.etactics.proxima.service.backend.playlist.Playlist;
import com.etactics.proxima.service.backend.playlist.PlaylistRepository;
import com.etactics.proxima.service.backend.playlist.PlaylistStatus;
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
public class ReminderService {

    private final PlaylistRepository playlistRepository;
    private final NominationRepository nominationRepository;
    private final ContributorRepository contributorRepository;

    public record PlaylistReminder(Playlist playlist, List<Contributor> contributorsToRemind, int daysUntilDeadline) {}

    public List<PlaylistReminder> findReminders() {
        ZonedDateTime startOfToday = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        List<Contributor> allWithEmail = contributorRepository.findByEmailIsNotNull();
        List<PlaylistReminder> reminders = new ArrayList<>();

        for (int days : List.of(1, 2, 3)) {
            Instant windowStart = startOfToday.plusDays(days).toInstant();
            Instant windowEnd = startOfToday.plusDays(days + 1).toInstant();
            List<Playlist> playlists = playlistRepository
                    .findByStatusAndDeadlineBetween(PlaylistStatus.OPEN, windowStart, windowEnd);

            for (Playlist playlist : playlists) {
                Set<Long> approvedNominatorIds = nominationRepository
                        .findByPlaylist_IdAndStatus(playlist.getId(), NominationStatus.APPROVED)
                        .stream()
                        .map(n -> n.getNominatedBy().getId())
                        .collect(Collectors.toSet());

                List<Contributor> toRemind = allWithEmail.stream()
                        .filter(c -> !approvedNominatorIds.contains(c.getId()))
                        .toList();

                if (!toRemind.isEmpty()) {
                    reminders.add(new PlaylistReminder(playlist, toRemind, days));
                }
            }
        }

        return reminders;
    }
}
