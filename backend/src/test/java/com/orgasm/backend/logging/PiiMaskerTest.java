package com.orgasm.backend.logging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "alice@example.com",
            "first.last+tag@sub.example.co.uk",
            "o'neil_99@example-mail.org",
    })
    void masksEmailAddresses(String email) {
        assertThat(PiiMasker.mask("Failed to send to " + email + " (SMTP down)"))
                .isEqualTo("Failed to send to " + PiiMasker.EMAIL_PLACEHOLDER + " (SMTP down)")
                .doesNotContain(email);
    }

    @Test
    void masksEveryOccurrence() {
        assertThat(PiiMasker.mask("a@x.io -> b@y.io"))
                .isEqualTo(PiiMasker.EMAIL_PLACEHOLDER + " -> " + PiiMasker.EMAIL_PLACEHOLDER);
    }

    @Test
    void leavesTextWithoutEmailsUntouched() {
        assertThat(PiiMasker.mask("Playlist 42 opened by contributor 7")).isEqualTo("Playlist 42 opened by contributor 7");
        assertThat(PiiMasker.mask("@mention is not an email")).isEqualTo("@mention is not an email");
        assertThat(PiiMasker.mask("")).isEmpty();
        assertThat(PiiMasker.mask(null)).isNull();
    }
}
