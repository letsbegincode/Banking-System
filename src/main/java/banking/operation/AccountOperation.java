package banking.operation;

import banking.account.Account;

import java.util.Collections;
import java.util.List;

public interface AccountOperation {
    OperationResult execute();

    String getDescription();

    default List<Integer> getInvolvedAccountNumbers() {
        return Collections.emptyList();
    }

    default List<Account> getAffectedAccounts() {
        return Collections.emptyList();
    }
}
