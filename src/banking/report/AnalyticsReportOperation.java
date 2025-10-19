package banking.report;

import banking.operation.AccountOperation;
import banking.operation.OperationResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class AnalyticsReportOperation implements AccountOperation {
    private final AnalyticsReportRequest request;
    private final AccountAnalyticsService analyticsService;
    private final List<AccountSnapshot> snapshots;
    private final CompletableFuture<AnalyticsReport> reportFuture;

    public AnalyticsReportOperation(AnalyticsReportRequest request,
            AccountAnalyticsService analyticsService,
            List<AccountSnapshot> snapshots) {
        this.request = Objects.requireNonNull(request, "request");
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService");
        this.snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        this.reportFuture = new CompletableFuture<>();
    }

    public CompletableFuture<AnalyticsReport> getReportFuture() {
        return reportFuture;
    }

    @Override
    public OperationResult execute() {
        try {
            AnalyticsReport report = analyticsService.analyze(snapshots, request);
            reportFuture.complete(report);
            return OperationResult.success("Analytics report generated for "
                    + request.getStartDate() + " to " + request.getEndDate());
        } catch (Exception e) {
            reportFuture.completeExceptionally(e);
            return OperationResult.failure("Analytics report failed: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Portfolio analytics report";
    }
}
