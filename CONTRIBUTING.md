# Contributing Guidelines

Thank you for investing time in the Banking System project! This document outlines how to report issues, propose enhancements, and submit code.

## How to Propose Changes
1. **Discuss First:** Open a GitHub issue for bugs or feature requests. Provide context, reproduction steps, and acceptance criteria.
2. **Fork & Branch:** Create a topic branch off `main` using a descriptive name (e.g., `feature/interest-batch`).
3. **Sync Often:** Rebase your branch on the latest `main` to minimize merge conflicts.
4. **Follow Coding Standards:**
   - Use idiomatic Java 8+.
   - Keep classes focused; prefer composition over inheritance when adding new behavior.
   - Avoid catching generic `Exception` unless rethrowing with context.
5. **Add Tests When Possible:** A lightweight regression harness lives under `src/banking/test`. Extend it or add complementary suites when you introduce new behavior, and document how to execute them.

## Development Workflow
1. Compile the project with `javac $(find src -name "*.java")`, execute automated checks via `java -cp src banking.test.BankTestRunner`, and then run the console with `java -cp src banking.BankingApplication` to verify interactive scenarios.
2. Ensure new features integrate with existing persistence by exercising create/deposit/withdraw flows.
3. Update documentation (`README`, `docs/`) when behavior or configuration changes.
4. Commit with conventional-style messages (e.g., `feat: add overdraft protection`).
5. Submit a pull request referencing related issues and summarizing test coverage.

## Code Review Expectations
- Reviews focus on correctness, clarity, and maintainability.
- Be responsive to feedback and push follow-up commits addressing comments.
- Prefer small, focused pull requests to large multi-feature drops.

## Community Standards
- Adhere to the [Code of Conduct](CODE_OF_CONDUCT.md).
- Respect the time and effort of maintainers by providing complete context and testing notes.

Happy hacking!
