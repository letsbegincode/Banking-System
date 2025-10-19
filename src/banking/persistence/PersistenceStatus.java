package banking.persistence;

import java.util.Objects;
import java.util.Optional;

/**
 * Summarizes the status of a persistence provider (primary or active fallback).
 */
public final class PersistenceStatus {
    private final boolean available;
    private final String provider;
    private final String message;
    private final Throwable error;

    private PersistenceStatus(boolean available, String provider, String message, Throwable error) {
        this.available = available;
        this.provider = provider == null ? "unknown" : provider;
        this.message = message == null ? "" : message;
        this.error = error;
    }

    public static PersistenceStatus available(String provider, String message) {
        return new PersistenceStatus(true, provider, message, null);
    }

    public static PersistenceStatus unavailable(String provider, String message) {
        return new PersistenceStatus(false, provider, message, null);
    }

    public static PersistenceStatus unavailable(String provider, String message, Throwable error) {
        return new PersistenceStatus(false, provider, message, error);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getProvider() {
        return provider;
    }

    public String getMessage() {
        return message;
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    public PersistenceStatus withMessage(String newMessage) {
        return new PersistenceStatus(available, provider, newMessage, error);
    }

    @Override
    public String toString() {
        return "PersistenceStatus{"
                + "provider='" + provider + '\''
                + ", available=" + available
                + ", message='" + message + '\''
                + (error != null ? ", error=" + error.getClass().getSimpleName() : "")
                + '}';
    }

    public PersistenceStatus withError(Throwable throwable) {
        return new PersistenceStatus(available, provider, message, throwable);
    }

    public PersistenceStatus unavailableCopy(String failureMessage, Throwable throwable) {
        return new PersistenceStatus(false, provider, failureMessage, throwable);
    }
}
