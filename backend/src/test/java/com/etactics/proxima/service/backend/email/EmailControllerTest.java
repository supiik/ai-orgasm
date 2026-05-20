package com.etactics.proxima.service.backend.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.etactics.proxima.service.backend.config.GlobalExceptionHandler;
import com.etactics.proxima.service.backend.config.VersionTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailControllerTest {

    MockMvc mvc;
    EmailService service = mock(EmailService.class);
    @SuppressWarnings("removal")
    ObjectMapper objectMapper = new ObjectMapper()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new EmailController(service))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void sendTest_returns202_whenValid() throws Exception {
        mvc.perform(post("/api/v1/emails/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendEmailRequest("user@example.com", "Hello", "World"))))
                .andExpect(status().isAccepted());

        verify(service).send(new EmailMessage("user@example.com", "Hello", "World"));
    }

    @Test
    void sendTest_returns400_whenToIsBlank() throws Exception {
        mvc.perform(post("/api/v1/emails/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendEmailRequest("", "Hello", "World"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.to").exists());
    }

    @Test
    void sendTest_returns400_whenToIsNotEmail() throws Exception {
        mvc.perform(post("/api/v1/emails/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendEmailRequest("not-an-email", "Hello", "World"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.to").exists());
    }

    @Test
    void sendTest_returns400_whenSubjectIsBlank() throws Exception {
        mvc.perform(post("/api/v1/emails/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendEmailRequest("user@example.com", "", "World"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.subject").exists());
    }

    @Test
    void sendTest_returns500_whenMailSenderFails() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(service).send(any(EmailMessage.class));

        mvc.perform(post("/api/v1/emails/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendEmailRequest("user@example.com", "Hello", "World"))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Internal server error"));
    }
}
