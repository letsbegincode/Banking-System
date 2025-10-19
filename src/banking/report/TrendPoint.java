package banking.report;

import java.time.LocalDate;
import java.util.Objects;

public record TrendPoint(LocalDate date,
                         double dailyNetChange,
                         double rollingAverageChange) {

    public TrendPoint {
        Objects.requireNonNull(date, "date");
    }
}
