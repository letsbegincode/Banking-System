# Troubleshooting

Use this reference when diagnosing common issues encountered during development or operations.

## Build & Compilation Issues
- **Missing JUnit Dependencies:** The project ships lightweight `org.junit.jupiter.api` stubs under `src/test/java`. Ensure the test sources are on the classpath alongside application classes during compilation.
- **Java Version Errors:** Confirm `java -version` reports JDK 17. Earlier versions may lack APIs used in the codebase.

## Runtime Errors
- **Database Connection Failures:** Verify `BANKING_JDBC_URL`, credentials, and network reachability. Test connectivity with your database client (e.g., `mysql -h <host> -u <user> -p`).
- **Schema Drift:** Run `bash deploy/scripts/run-migrations.sh` after pulling new changes to keep the database schema aligned with the application expectations.
- **Authentication Problems:** All HTTP requests must include the `Authorization: Bearer <token>` header. Generate a new token via `/auth/login` if the existing token expires.

## Data Integrity Concerns
- **Stale Cache Reads:** Adjust cache TTLs (`CACHE_TTL_SECONDS`, etc.) or switch to the `none` provider for real-time reads when debugging.
- **Snapshot Persistence:** Ensure `BANKING_DATA_PATH` points to a writable location. The console exits should be graceful to flush state to disk.

## Docker & CI
- **Docker Hub Outages:** CI and local scripts perform availability checks before building images. Retry once the registry recovers, or build from cached base images if available.
- **Container Port Conflicts:** The API container exposes port `8080`. Stop other services using the same port or update the mapping in `deploy/compose/docker-compose.yml`.

## Support Channels
- Review existing issues and documentation before opening new tickets.
- Include reproduction steps, environment details, and relevant logs when seeking assistance.

