# Banking System Wiki

Welcome to the Banking System wiki. This knowledge base centralizes product documentation, architecture notes, and operational runbooks for the modular Java banking platform contained in this repository. Use the navigation links below to jump to areas of interest, or browse sequentially to gain a holistic understanding of the project.

## Quick Navigation
- [Platform Overview](#platform-overview)
- [Architecture Summary](Architecture.md)
- [Environment Setup](Setup.md)
- [Operations & Runbooks](Operations.md)
- [Testing Guide](Testing.md)
- [Troubleshooting](Troubleshooting.md)

## Platform Overview
The Banking System simulates common retail banking flows such as customer onboarding, multi-account management, and transaction processing. Core capabilities include:

- **Customer and account lifecycle management** with Savings, Current, and Fixed Deposit account types.
- **Transaction execution** for deposits, withdrawals, transfers, interest postings, and audit trails.
- **Console and HTTP interfaces** backed by the shared domain model for teller workflows and automation.
- **Observability hooks** via notifiers and loggers that react to account events.
- **Persistence and caching** abstraction layers supporting in-memory demos and JDBC-backed deployments.

For deeper background, review the [Architecture Summary](Architecture.md) or the existing reference documents in `docs/`.

## Getting Started
If you are new to the project, start with:
1. [Environment Setup](Setup.md) – install prerequisites and run the applications locally.
2. [Testing Guide](Testing.md) – learn how to compile, run smoke tests, and execute CI workflows.
3. [Operations & Runbooks](Operations.md) – understand day-two tasks such as migrations, backups, and monitoring.

## Contributing
Community expectations are covered in [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) and [CONTRIBUTING.md](../CONTRIBUTING.md). Review them before submitting issues or pull requests.

## Additional Resources
- System design diagrams: [docs/architecture-system-design.md](../docs/architecture-system-design.md)
- Detailed component breakdown: [docs/architecture-low-level.md](../docs/architecture-low-level.md)
- Database schema reference: [docs/database-schema.md](../docs/database-schema.md)

