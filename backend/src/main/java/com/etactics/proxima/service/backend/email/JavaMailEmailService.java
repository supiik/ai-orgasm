package com.etactics.proxima.service.backend.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JavaMailEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mm = new SimpleMailMessage();
        mm.setFrom(from);
        mm.setTo(message.to());
        mm.setSubject(message.subject());
        mm.setText(message.body());
        mailSender.send(mm);
    }
}
