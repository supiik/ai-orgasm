package com.orgasm.backend.logging;

import java.util.regex.Pattern;

/**
 * Last line of defence against PII reaching the log sink: replaces anything that looks like an
 * email address with a fixed placeholder. Applied by {@link StructuredLogCustomizer} to every
 * string value in the JSON output (message, MDC, key-value pairs, exception message, stack trace),
 * so a stray {@code toString()} of a Contributor or a JPA constraint-violation message containing
 * an address is scrubbed even when the call site forgot.
 *
 * <p>This is a safety net, not a licence: log statements must still be written to log ids, never
 * emails/names — the masker can't recognise a bare personal name.
 */
public final class PiiMasker {

    public static final String EMAIL_PLACEHOLDER = "[redacted-email]";

    // Deliberately permissive local-part/domain match: false positives cost a placeholder,
    // false negatives cost a leaked address.
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~.\\-]+@[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?)+");

    private PiiMasker() {}

    public static String mask(String value) {
        if (value == null || value.indexOf('@') < 0) {
            return value;
        }
        return EMAIL.matcher(value).replaceAll(EMAIL_PLACEHOLDER);
    }
}
