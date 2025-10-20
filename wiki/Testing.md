# Testing Guide

The Banking System repository favors lightweight tooling and deterministic smoke tests. This guide outlines available checks and how to run them locally or in CI.

## Compilation
Compile all sources with `javac` to ensure the codebase is buildable:
```bash
mkdir -p build/classes
find src -name '*.java' > sources.txt
javac -d build/classes @sources.txt
```

## Unit & Integration Tests
The repository includes a suite of JUnit 5-style tests under `src/test/java`. Because the project avoids a heavyweight build system, the tests compile against lightweight stubs that mimic the `org.junit.jupiter.api` annotations and assertion APIs. To execute the tests:

1. Compile sources and tests onto the same classpath.
2. Use a Java test runner of your choice (e.g., an IDE, `java org.junit.platform.console.ConsoleLauncher`).

Example using the JUnit Platform Console Launcher (requires downloading the launcher JAR):
```bash
java -jar junit-platform-console-standalone.jar \
  -cp build/classes:src/test/java \
  --scan-classpath
```

## Persistence Smoke Test
A minimal integration check ensures persistence flows work end-to-end:
```bash
java -cp build/classes banking.test.PersistenceSmokeTest
```
The test provisions a temporary storage directory, runs a sample transaction, and verifies the serialized state.

## Database Migrations
Run migrations with:
```bash
BANKING_DATA_PATH="$(pwd)/tmp/data/banking_state.properties" bash deploy/scripts/run-migrations.sh
```
When `BANKING_JDBC_URL` is not set, the script logs the omission and exits successfully, keeping CI pipelines green.

## Continuous Integration Workflow
GitHub Actions automates the following steps on pull requests:
1. Checkout and compile all Java sources.
2. Package runnable JARs for the console and API applications.
3. Run filesystem-backed migrations and the persistence smoke test.
4. Attempt container builds when Docker Hub is reachable (guarded by a registry availability probe).

Inspect `.github/workflows/ci.yml` for the authoritative pipeline definition.

