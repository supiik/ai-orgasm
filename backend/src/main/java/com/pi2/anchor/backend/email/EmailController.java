package com.pi2.anchor.backend.email;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/emails", version = "1")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendTest(@RequestBody @Valid SendEmailRequest request) {
        emailService.send(new EmailMessage(request.to(), request.subject(), request.body()));
    }
}
