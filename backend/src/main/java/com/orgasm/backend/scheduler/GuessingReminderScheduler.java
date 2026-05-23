package com.orgasm.backend.scheduler;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.email.EmailMessage;
import com.orgasm.backend.email.EmailService;
import com.orgasm.backend.reminder.GuessingReminderService;
import com.orgasm.backend.reminder.GuessingReminderService.GuessingReminder;
import com.orgasm.backend.tenant.TenantContext;
import com.orgasm.backend.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GuessingReminderScheduler {

    private final GuessingReminderService guessingReminderService;
    private final EmailService emailService;
    private final TenantRepository tenantRepository;

    @Scheduled(cron = "${app.scheduler.guessing-reminder-cron:0 0 9 * * *}")
    public void sendReminders() {
        tenantRepository.findAll().forEach(tenant -> {
            TenantContext.set(tenant.getId());
            try {
                var reminders = guessingReminderService.findReminders();
                log.info("Tenant {}: sending guessing deadline reminders for {} playlist(s)", tenant.getSlug(), reminders.size());
                for (GuessingReminder reminder : reminders) {
                    for (Contributor contributor : reminder.contributorsToRemind()) {
                        try {
                            emailService.send(buildMessage(reminder, contributor));
                        } catch (Exception e) {
                            log.warn("Failed to send guessing reminder to {}: {}", contributor.getEmail(), e.getMessage());
                        }
                    }
                }
            } finally {
                TenantContext.clear();
            }
        });
    }

    private EmailMessage buildMessage(GuessingReminder reminder, Contributor contributor) {
        String dayWord = reminder.daysUntilDeadline() == 1 ? "1 day" : reminder.daysUntilDeadline() + " days";
        String subject = String.format("Reminder: guess who nominated songs in \"%s\" — %s left", reminder.playlist().getName(), dayWord);
        String body = String.format("""
                Hi %s,

                The guessing deadline for playlist "%s" is in %s.

                You haven't submitted your guesses yet — don't miss your chance!

                Guessing deadline: %s
                """,
                contributor.getName(),
                reminder.playlist().getName(),
                dayWord,
                reminder.playlist().getGuessingDeadline());
        return new EmailMessage(contributor.getEmail(), subject, body);
    }
}
