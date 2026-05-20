package com.etactics.proxima.sal.backend.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;

    JavaMailEmailService service;

    @BeforeEach
    void setup() {
        service = new JavaMailEmailService(mailSender);
        ReflectionTestUtils.setField(service, "from", "noreply@proxima-sal.local");
    }

    @Test
    void send_dispatches_correct_message() {
        service.send(new EmailMessage("user@example.com", "Hello", "World"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("noreply@proxima-sal.local");
        assertThat(sent.getTo()).containsExactly("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Hello");
        assertThat(sent.getText()).isEqualTo("World");
    }

    @Test
    void send_propagates_mail_exception() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.send(new EmailMessage("u@x.com", "s", "b")))
                .isInstanceOf(org.springframework.mail.MailException.class);
    }
}
