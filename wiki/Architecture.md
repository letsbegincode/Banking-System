# Architecture Summary

This page highlights the core components of the Banking System and how they collaborate. For detailed diagrams and design rationale, cross-reference the architecture documents in the `docs/` directory.

## Layered View
The platform follows a layered architecture centered on the `Bank` aggregate:

- **Presentation Layer**
  - `ConsoleUI` orchestrates teller interactions and maps console commands to domain actions.
  - `ApiApplication` adapts the domain model to HTTP, exposing health, metrics, and account operations endpoints.
- **Domain Layer**
  - `Bank` coordinates account lifecycle events, delegates transaction execution, and maintains invariants.
  - The `Account` hierarchy (Savings, Current, FixedDeposit) encapsulates product-specific rules and balances.
  - `AccountOperation` implementations represent transactional commands with validation and idempotency safeguards.
- **Infrastructure Layer**
  - `AccountRepository` abstracts persistence, with JDBC and in-memory implementations selectable at runtime.
  - `MigrationRunner` executes SQL migrations before the application serves traffic.
  - `CacheProvider` implementations accelerate reads by caching account snapshots and derived balances.

A simplified component diagram is available in [docs/architecture-system-design.md](../docs/architecture-system-design.md), while low-level class interactions are documented in [docs/architecture-low-level.md](../docs/architecture-low-level.md).

## Key Cross-Cutting Concerns
- **Observability:** The observer pattern wires `ConsoleNotifier` and `TransactionLogger` to account events for operator feedback and auditing.
- **Configuration:** Environment variables and JVM properties (e.g., `CACHE_PROVIDER`, `BANKING_STORAGE_MODE`) control repository selection, cache policies, and integration endpoints.
- **Concurrency:** Transaction processing uses a queue-backed executor to serialize account mutations and guarantee consistency.

## Persistence Options
- **In-Memory Snapshot Repository:** Default for local demos; stores serialized aggregates on the filesystem (`BANKING_DATA_PATH`).
- **JDBC Repository:** Targets relational databases such as MySQL. Requires `BANKING_STORAGE_MODE=jdbc`, `BANKING_JDBC_URL`, and credentials. Schema definitions live in [docs/database-schema.md](../docs/database-schema.md).

## Caching Strategy
The `CacheProviderFactory` chooses between:
- `InMemoryCacheProvider` (default): TTL-driven caching with configurable expiration.
- `NoOpCacheProvider`: Disabled caching for troubleshooting or cold-read environments.

Fine-grained TTLs can be set with `CACHE_ACCOUNT_TTL_SECONDS` and `CACHE_BALANCE_TTL_SECONDS`. See [src/main/java/banking/cache](../src/main/java/banking/cache) for implementation details.

## Extension Points
- **Repositories:** Implement `AccountRepository` to support new storage backends.
- **Cache Providers:** Add a new `CacheProvider` implementation and extend the factory for alternative caching layers (e.g., Redis).
- **Interfaces:** Additional presentation layers can wrap the `Bank` aggregate without modifying domain logic.

