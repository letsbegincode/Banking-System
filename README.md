# Banking System Project

## Overview

This Java-based console application simulates a simple banking system, allowing users to manage bank accounts, perform transactions, and view account details. The application supports serialization to save and load account data.

## Features

- **Account Management**
  - Create savings or current accounts.
  - Delete accounts.
  - View all accounts.

- **Transactions**
  - Deposit and withdraw funds.
  - Interest calculation for savings accounts.
  - Transaction history per account.

- **Persistence**
  - Serialize and deserialize banking data for persistent storage.

## How to Use

1. **Run the Application**: Compile and execute the `BankDemo` class.
2. **Choose Actions**: Use the menu to perform actions:
   - `1`: Create a new account.
   - `2`: Deposit funds.
   - `3`: Withdraw funds.
   - `4`: Display balance and transaction history.
   - `5`: View all accounts.
   - `6`: Delete an account.
   - `7`: Exit the application (data is saved automatically).

## Technical Details

- **Serialization**: Account and transaction data are stored in `banking_system.ser`.
- **Concurrency**: Threads are used for deposit and withdrawal operations.
- **Validation**: Ensures valid account details and transaction amounts.

## Requirements

- Java 8 or higher.

## Run Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/letsbegincode/java-Projects.git
   ```
2. Compile the project:
   ```bash
   javac BankDemo.java
   ```
3. Run the project:
   ```bash
   java BankDemo
   ```

## Future Improvements

- Add user authentication.
- Enhance account types with customizable interest rates.
- Implement a graphical user interface (GUI).
