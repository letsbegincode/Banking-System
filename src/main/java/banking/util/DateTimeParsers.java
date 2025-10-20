package banking.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Utility helpers for parsing date/time values originating from a variety of
 * persistence backends. The production system has evolved over time and stores
 * timestamps in multiple formats (date only, local date-times, ISO instants,
 * legacy space separated strings, ...). Centralising the parsing logic keeps
 * the rest of the codebase simple while making it resilient to older
 * snapshots.
 */
public final class DateTimeParsers {
    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateTimeParsers() {
    }

    /**
     * Parses the creation date persisted with an {@code AccountSnapshot}.
     * Historically this field has been saved either as a date (yyyy-MM-dd), a
     * full ISO local date-time, or as a legacy space separated date-time. Some
     * migration tooling has also written the value as an instant (with or
     * without an offset).
     */
    public static LocalDateTime parseAccountCreationDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        LocalDateTime parsed = parseFlexibleDateTime(value.trim(), true);
        if (parsed == null) {
            throw new IllegalArgumentException("Unsupported creation date format: " + value);
        }
        return parsed;
    }

    /**
     * Parses transaction timestamps coming from snapshots or the database. The
     * accepted formats mirror the ones handled by
     * {@link #parseAccountCreationDate(String)}.
     */
    public static LocalDateTime parseTransactionTimestamp(String value) {
        Objects.requireNonNull(value, "value");
        LocalDateTime parsed = parseFlexibleDateTime(value.trim(), false);
        if (parsed == null) {
            throw new IllegalArgumentException("Unsupported transaction timestamp format: " + value);
        }
        return parsed;
    }

    private static LocalDateTime parseFlexibleDateTime(String value, boolean allowNowFallback) {
        if (value.isEmpty()) {
            return allowNowFallback ? LocalDateTime.now() : null;
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // continue
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // continue
        }

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // continue
        }

        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
            // continue
        }

        try {
            return LocalDateTime.parse(value, LEGACY_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // continue
        }

        try {
            return LocalDate.parse(value, LEGACY_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // continue
        }

        return null;
    }
}
