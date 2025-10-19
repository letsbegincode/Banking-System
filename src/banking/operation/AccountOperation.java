package banking.operation;

public interface AccountOperation {
    OperationResult execute();

    String getDescription();
}
