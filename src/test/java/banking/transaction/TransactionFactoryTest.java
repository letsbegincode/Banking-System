package banking.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import banking.snapshot.TransactionSnapshot;
import banking.snapshot.TransactionType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class TransactionFactoryTest {

    @Test
    void parsesIsoInstantTimestamps() {
        TransactionSnapshot snapshot = new TransactionSnapshot(TransactionType.DEPOSIT, 50.0,
                "2025-10-20T13:45:12Z", "abc12345", null, null);

        BaseTransaction transaction = TransactionFactory.fromSnapshot(snapshot);

        assertEquals(LocalDateTime.ofInstant(Instant.parse("2025-10-20T13:45:12Z"), ZoneId.systemDefault()),
                transaction.getTimestamp());
    }

    @Test
    void parsesLegacyDateTimeTimestamps() {
        TransactionSnapshot snapshot = new TransactionSnapshot(TransactionType.DEPOSIT, 25.0,
                "2025-10-20 08:30:00", "def67890", null, null);

        BaseTransaction transaction = TransactionFactory.fromSnapshot(snapshot);

        assertEquals(LocalDateTime.of(2025, 10, 20, 8, 30), transaction.getTimestamp());
    }

    @Test
    void parsesDateOnlyTimestamps() {
        TransactionSnapshot snapshot = new TransactionSnapshot(TransactionType.DEPOSIT, 10.0,
                "2025-10-20", "ghi13579", null, null);

        BaseTransaction transaction = TransactionFactory.fromSnapshot(snapshot);

        assertEquals(LocalDate.of(2025, 10, 20).atStartOfDay(), transaction.getTimestamp());
    }
}
