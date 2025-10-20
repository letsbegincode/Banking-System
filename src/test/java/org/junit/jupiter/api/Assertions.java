package org.junit.jupiter.api;

import java.util.Objects;
import java.util.function.Supplier;

import org.junit.jupiter.api.function.ThrowingSupplier;

public final class Assertions {
    private Assertions() {
    }

    public static void assertEquals(double expected, double actual, double delta) {
        if (Double.isNaN(expected) || Double.isNaN(actual)) {
            if (!(Double.isNaN(expected) && Double.isNaN(actual))) {
                throw new AssertionError("Expected " + expected + " but was " + actual);
            }
            return;
        }
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition expected to be true but was false");
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertTrue(boolean condition, Supplier<String> messageSupplier) {
        if (!condition) {
            String message = messageSupplier == null ? "Condition expected to be true but was false" : messageSupplier.get();
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object actual) {
        if (actual == null || !expectedType.isInstance(actual)) {
            throw new AssertionError("Expected instance of " + expectedType.getName() + " but was " + (actual == null ? "null" : actual.getClass().getName()));
        }
        return expectedType.cast(actual);
    }

    public static <T extends Throwable> T assertThrows(Class<T> expectedType, ThrowingSupplier<?> executable) {
        try {
            executable.get();
        } catch (Throwable throwable) {
            if (expectedType.isInstance(throwable)) {
                return expectedType.cast(throwable);
            }
            throw new AssertionError("Expected exception of type " + expectedType.getName() + " but caught " + throwable.getClass().getName(), throwable);
        }
        throw new AssertionError("Expected exception of type " + expectedType.getName() + " but nothing was thrown");
    }
}
