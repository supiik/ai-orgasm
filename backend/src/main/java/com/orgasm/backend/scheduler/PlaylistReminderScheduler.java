package com.orgasm.backend.scheduler;

import com.orgasm.backend.email.EmailMessage;
import com.orgasm.backend.email.EmailService;
import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.reminder.ReminderService;
import com.orgasm.backend.reminder.ReminderService.PlaylistReminder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlaylistReminderScheduler {

    private final ReminderService reminderService;
    private final EmailService emailService;

    @Scheduled(cron = "${app.scheduler.reminder-cron:0 0 9 * * *}")
    public void sendReminders() {
        var reminders = reminderService.findReminders();
        log.info("Sending deadline reminders: {} playlist(s) with pending contributors", reminders.size());
        for (PlaylistReminder reminder : reminders) {
            for (Contributor contributor : reminder.contributorsToRemind()) {
                try {
                    emailService.send(buildMessage(reminder, contributor));
                } catch (Exception e) {
                    log.warn("Failed to send reminder to {}: {}", contributor.getEmail(), e.getMessage());
                }
            }
        }
    }

    private EmailMessage buildMessage(PlaylistReminder reminder, Contributor contributor) {
        String dayWord = reminder.daysUntilDeadline() == 1 ? "1 day" : reminder.daysUntilDeadline() + " days";
        String subject = String.format("Reminder: \"%s\" deadline in %s", reminder.playlist().getName(), dayWord);
        String body = String.format("""
                Hi %s,

                This is a reminder that the nomination deadline for playlist "%s" is in %s.

                You haven't submitted an approved nomination yet — there's still time!

                Deadline: %s
                """,
                contributor.getName(),
                reminder.playlist().getName(),
                dayWord,
                reminder.playlist().getDeadline());
        return new EmailMessage(contributor.getEmail(), subject, body);
    }
}
