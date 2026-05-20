package com.etactics.proxima.sal.backend.scheduler;

import com.etactics.proxima.sal.backend.contributor.Contributor;
import com.etactics.proxima.sal.backend.email.EmailMessage;
import com.etactics.proxima.sal.backend.email.EmailService;
import com.etactics.proxima.sal.backend.playlist.Playlist;
import com.etactics.proxima.sal.backend.playlist.PlaylistStatus;
import com.etactics.proxima.sal.backend.reminder.ReminderService;
import com.etactics.proxima.sal.backend.reminder.ReminderService.PlaylistReminder;
import com.etactics.proxima.sal.backend.tenant.Tenant;
import com.etactics.proxima.sal.backend.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistReminderSchedulerTest {

    @Mock ReminderService reminderService;
    @Mock EmailService emailService;
    @Mock TenantRepository tenantRepository;
    @InjectMocks PlaylistReminderScheduler scheduler;

    static Tenant defaultTenant() {
        return new Tenant(1L, "default", "Default Tenant");
    }

    @BeforeEach
    void stubTenants() {
        when(tenantRepository.findAll()).thenReturn(List.of(defaultTenant()));
    }

    static Contributor contributor(long id, String name, String email) {
        var c = new Contributor();
        c.setId(id);
        c.setName(name);
        c.setEmail(email);
        return c;
    }

    static Playlist openPlaylist(String name, Instant deadline) {
        var p = new Playlist();
        p.setId(1L);
        p.setName(name);
        p.setStatus(PlaylistStatus.OPEN);
        p.setDeadline(deadline);
        return p;
    }

    @Test
    void sendReminders_sendsOneEmailPerContributor() {
        var c1 = contributor(1L, "Alice", "alice@example.com");
        var c2 = contributor(2L, "Bob", "bob@example.com");
        var playlist = openPlaylist("Summer Mix", Instant.now().plusSeconds(86400));
        var reminder = new PlaylistReminder(playlist, List.of(c1, c2), 1);

        when(reminderService.findReminders()).thenReturn(List.of(reminder));

        scheduler.sendReminders();

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService, times(2)).send(captor.capture());

        var sent = captor.getAllValues();
        assertThat(sent).extracting(EmailMessage::to)
                .containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
        assertThat(sent.get(0).subject()).contains("Summer Mix").contains("1 day");
        assertThat(sent.get(0).body()).contains("Alice").contains("Summer Mix").contains("1 day");
    }

    @Test
    void sendReminders_usesCorrectDayWordForMultipleDays() {
        var contributor = contributor(1L, "Alice", "alice@example.com");
        var playlist = openPlaylist("Winter Mix", Instant.now().plusSeconds(3 * 86400L));
        var reminder = new PlaylistReminder(playlist, List.of(contributor), 3);

        when(reminderService.findReminders()).thenReturn(List.of(reminder));

        scheduler.sendReminders();

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().subject()).contains("3 days");
        assertThat(captor.getValue().body()).contains("3 days");
    }

    @Test
    void sendReminders_continuesSending_whenOneEmailFails() {
        var c1 = contributor(1L, "Alice", "alice@example.com");
        var c2 = contributor(2L, "Bob", "bob@example.com");
        var playlist = openPlaylist("Autumn Mix", Instant.now().plusSeconds(86400));
        var reminder = new PlaylistReminder(playlist, List.of(c1, c2), 1);

        when(reminderService.findReminders()).thenReturn(List.of(reminder));
        doThrow(new MailSendException("SMTP down")).when(emailService).send(argThat(m -> m.to().equals("alice@example.com")));

        scheduler.sendReminders();

        verify(emailService, times(2)).send(any());
    }

    @Test
    void sendReminders_doesNothing_whenNoReminders() {
        when(reminderService.findReminders()).thenReturn(List.of());

        scheduler.sendReminders();

        verifyNoInteractions(emailService);
    }
}
