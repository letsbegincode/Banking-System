package banking.operation;

import banking.account.Account;

import java.util.Collection;
import java.util.Collections;

public interface AccountOperation {
    OperationResult execute();

    String getDescription();

    default Collection<Account> affectedAccounts() {
        return Collections.emptyList();
    }
}
