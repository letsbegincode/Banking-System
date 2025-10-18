# System Design Overview

This guide captures the macro-architecture of the Banking System, focusing on deployment, data flow, and strategies for scaling the console experience into larger environments.

## Context & Goals
- Provide an operator-focused interface for managing customer accounts and transactions.
- Preserve durability of financial records while keeping the system simple enough for educational and demo purposes.
- Offer a foundation that can evolve into a multi-channel banking service.

## Logical Architecture
At runtime the application behaves as a monolithic service composed of presentation, domain, and infrastructure layers. The current packaging is a console executable, but the same layers can be exposed through alternative adapters (REST, messaging).

```plantuml
@startuml
package "Presentation" {
  [ConsoleUI]
}
package "Domain" {
  [Bank]
  [AccountFactory]
  [Account Hierarchy]
  [AccountOperation Commands]
  [Observers]
}
package "Infrastructure" {
  [BankDAO]
  [ExecutorService]
  [Serialized Store]
}
[ConsoleUI] --> [Bank]
[Bank] --> [AccountFactory]
[Bank] --> [Account Hierarchy]
[Bank] --> [AccountOperation Commands]
[Account Hierarchy] --> [Observers]
[Bank] --> [BankDAO]
[Bank] --> [ExecutorService]
[BankDAO] --> [Serialized Store]
@enduml
```

## Data Flow
1. Operators initiate actions from the CLI. Inputs are validated and mapped to command objects.
2. Commands execute within the `Bank` service, which orchestrates transactional updates across the relevant `Account` instances.
3. Each mutation appends a `BaseTransaction` record, enabling audit trails and replay.
4. Persistence writes the mutated bank aggregate to `banking_system.ser`. Startup reads the file back into memory.
5. Observers emit feedback to the console and structured logs for operators.

```mermaid
flowchart LR
    UI[ConsoleUI] --> CMD[AccountOperation Commands]
    CMD --> BANK[Bank Service]
    BANK --> ACC[Account Instances]
    ACC --> TXN[Transaction Ledger]
    BANK --> DAO[BankDAO]
    DAO --> STORE[(banking_system.ser)]
    ACC --> OBS[Observers]
    OBS --> LOGS[Console Output / Logs]
```

## Scalability Considerations
- **Thread Pool Sizing:** The executor currently uses a fixed thread pool. Increase the pool or migrate to a work-stealing pool when adding high-volume batch jobs.
- **External Storage:** Replace serialization with a transactional database (PostgreSQL, MySQL) to support concurrent clients and reporting workloads.
- **Service Interfaces:** Wrap the domain layer in REST or gRPC endpoints to support distributed user interfaces and automation.
- **Horizontal Scale:** Once stateless adapters exist, run multiple instances behind a load balancer and rely on the shared database for consistency.

## Infrastructure & Deployment
- **Local:** Java CLI application executed on developer machines. Source-controlled `banking_system.ser` should be excluded from commits to prevent leaking data.
- **Staging/Production Concept:** Package the application as a runnable JAR. Deploy to a container or VM with scheduled backups of the persistence file or database.
- **Observability:** Extend `TransactionLogger` to integrate with structured logging frameworks (e.g., Logback). Capture metrics for operation latency and failure counts.
- **Security:** Introduce secrets management for future database credentials and enforce TLS when exposing remote APIs.

## Disaster Recovery
- Store serialized snapshots (or database backups) offsite.
- Validate backups by performing periodic restore drills in a staging environment.
- Automate log shipping to aid in reconstructing transaction sequences during investigations.
