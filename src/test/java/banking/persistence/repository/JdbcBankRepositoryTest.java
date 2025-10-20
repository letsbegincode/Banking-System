package banking.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JdbcBankRepositoryTest {

    private Method parseCreationDate;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        parseCreationDate = JdbcBankRepository.class.getDeclaredMethod("parseCreationDate", String.class);
        parseCreationDate.setAccessible(true);
    }

    private LocalDateTime invokeParse(String input) {
        try {
            return (LocalDateTime) parseCreationDate.invoke(null, input);
        } catch (IllegalAccessException e) {
            throw new AssertionError("parseCreationDate should be accessible", e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(e.getTargetException());
        }
    }

    @Nested
    @DisplayName("parseCreationDate")
    class ParseCreationDate {

        @Test
        @DisplayName("accepts ISO local date without time component")
        void acceptsDateOnly() {
            LocalDateTime parsed = invokeParse("2025-10-20");

            assertEquals(LocalDate.of(2025, 10, 20).atStartOfDay(), parsed);
        }

        @Test
        @DisplayName("accepts ISO local date time string")
        void acceptsIsoDateTime() {
            LocalDateTime parsed = invokeParse("2025-10-20T13:45:12");

            assertEquals(LocalDate.of(2025, 10, 20).atTime(13, 45, 12), parsed);
        }

        @Test
        @DisplayName("rejects unsupported formats with descriptive error")
        void rejectsUnsupportedFormats() {
            assertThrows(IllegalArgumentException.class, () -> invokeParse("10/20/2025"));
        }
    }
}
