# Low-Level Architecture

This document describes how the Java modules collaborate within the Banking System application. It focuses on class responsibilities, control flow, and extensibility points used by operations teams and contributors.

## Module Interaction Summary
The runtime orchestrates user commands from the console through a set of cohesive modules:
- **ConsoleUI** collects input and maps it to `AccountOperation` commands before delegating to the bank.
- **Bank** owns the account registry and manages the asynchronous operation queue/executor as well as interest routines.
- **AccountFactory** creates concrete account types while encapsulating initialization rules.
- **Account** subclasses (`SavingsAccount`, `CurrentAccount`, `FixedDepositAccount`) enforce balance policies and interest behavior.
- **BankDAO** persists and restores the `Bank` aggregate via Java serialization.
- **Observers** (`ConsoleNotifier`, `TransactionLogger`) subscribe to account events to provide feedback and audit trails.

```mermaid
sequenceDiagram
    participant User
    participant ConsoleUI
    participant Bank
    participant Queue as OperationQueue
    participant Executor as ExecutorService
    participant Account
    participant BankDAO
    participant Observer as Observers

    User->>ConsoleUI: Request operation (e.g., deposit)
    ConsoleUI->>Bank: queueOperation(operation)
    Bank->>Queue: enqueue(operation)
    Bank->>Executor: submit(poll())
    Executor->>Account: operation.execute()
    Account-->>Observers: notify(transaction)
    ConsoleUI->>BankDAO: saveBank(bank) on exit
    BankDAO-->>ConsoleUI: confirmation/exception
```

## Class Design

```mermaid
classDiagram
    class ConsoleUI {
        -Scanner scanner
        -Bank bank
        +start()
        +displayWelcomeBanner()
        +displayMainMenu()
        +createAccountMenu()
        +accountOperationsMenu()
        +generateReportsMenu()
    }
    class Bank {
        -Map~Integer, Account~ accounts
        -Queue~AccountOperation~ operationQueue
        -ExecutorService executorService
        +addObserver(AccountObserver)
        +createAccount(userName, type, initialDeposit)
        +closeAccount(int)
        +updateAccountHolderName(int, String)
        +getAccount(int)
        +queueOperation(AccountOperation)
        +executePendingOperations()
        +addInterestToAllSavingsAccounts(): int
        +shutdown()
    }
    class Account {
        <<abstract>>
        -int accountNumber
        -String userName
        -double balance
        +deposit(double)
        +withdraw(double)
        +transfer(double, Account)
        +addInterest()
        +getAccountType()
        +getTransactions()
    }
    class SavingsAccount
    class CurrentAccount
    class FixedDepositAccount
    class AccountFactory {
        +createAccount(type, userName)
    }
    class BankDAO {
        +loadBank()
        +saveBank(Bank)
    }
    class AccountOperation {
        <<interface>>
        +execute()
        +getDescription()
    }
    class BaseTransaction {
        <<abstract>>
        +getAmount()
        +getType()
        +getDateTime()
    }
    class ConsoleNotifier
    class TransactionLogger

    ConsoleUI --> Bank
    ConsoleUI --> BankDAO
    Bank --> AccountFactory
    AccountFactory --> Account
    Account <|-- SavingsAccount
    Account <|-- CurrentAccount
    Account <|-- FixedDepositAccount
    BankDAO --> Bank
    Account --> BaseTransaction
    Account --> ConsoleNotifier
    Account --> TransactionLogger
    AccountOperation <|.. DepositOperation
    AccountOperation <|.. WithdrawOperation
    AccountOperation <|.. TransferOperation
    BaseTransaction <|-- DepositTransaction
    BaseTransaction <|-- WithdrawalTransaction
    BaseTransaction <|-- TransferTransaction
    BaseTransaction <|-- TransferReceiveTransaction
    BaseTransaction <|-- InterestTransaction
```

## Execution Flow Details
1. `BankingApplication` boots by calling `BankDAO.loadBank()`. If the serialized file is absent, a new `Bank` is constructed and passed into `ConsoleUI`.
2. When the operator selects an action, `ConsoleUI` builds the appropriate `AccountOperation` (e.g., `DepositOperation`) and invokes `Bank.queueOperation`.
3. `Bank` drains the internal queue through `executePendingOperations()`, delegating each operation to the `ExecutorService`. Operations mutate account state in a thread-safe manner and append concrete `BaseTransaction` entries.
4. Accounts broadcast the resulting transaction through the observer list. `ConsoleNotifier` prints feedback; `TransactionLogger` writes audit lines.
5. On exit, `ConsoleUI` calls `BankDAO.saveBank(bank)`, updating `banking_system.ser` with the latest serialized snapshot.

## Extension Points
- **New account type:** Implement a subclass of `Account` and update `AccountFactory` to instantiate it.
- **Additional operations:** Add a new `AccountOperation` implementation and expose it in `ConsoleUI`.
- **Alternative persistence:** Replace `BankDAO` with a repository using JDBC or an ORM; the `Bank` contract stays unchanged.
- **New observers:** Implement `AccountObserver` (see `ConsoleNotifier`) to tap into the event stream without touching business logic.
