# Operations & Runbooks

This section documents day-two operational procedures for the Banking System, covering migrations, backups, monitoring, and container automation.

## Database Migrations
Use the provided script to manage schema changes:
```bash
BANKING_DATA_PATH="$(pwd)/tmp/data/banking_state.properties" bash deploy/scripts/run-migrations.sh
```
- When `BANKING_JDBC_URL` is **unset**, the script reports the omission and skips JDBC migrations (useful for snapshot-only demos).
- With `BANKING_JDBC_URL` defined, SQL migrations execute before application startup. Ensure credentials (`BANKING_DB_USER`, `BANKING_DB_PASSWORD`) have the necessary privileges.

## Backup & Restore
- **Snapshot Mode:** Copy the `BANKING_DATA_PATH` file to archive account state. Restoring involves replacing the file before launching the application.
- **JDBC Mode:** Rely on database-native tooling (`mysqldump`, point-in-time recovery, or managed service backups). Store dumps securely and test restores regularly.
- **Resetting the Environment:** Drop and recreate the database schema or delete the snapshot file to start with a clean slate.

## Monitoring & Observability
- **Console Notifications:** Real-time feedback for account lifecycle events via `ConsoleNotifier`.
- **Transaction Logs:** `TransactionLogger` records operations with timestamps for auditing.
- **HTTP Endpoints:**
  - `/healthz` – liveness check
  - `/metrics` – exposure of internal counters (requires authentication)
  - `/accounts` – useful for spot-checking data integrity

Integrate these endpoints with your monitoring stack (Prometheus, health probes, etc.) to track uptime and performance.

## Container Automation
### Docker Images
Two Dockerfiles live under `deploy/`:
- `Dockerfile.console`
- `Dockerfile.api`

Build images locally:
```bash
docker build -f deploy/Dockerfile.console -t banking-console:local .
docker build -f deploy/Dockerfile.api -t banking-api:local .
```

### Docker Compose
The repository ships a Docker Compose configuration that provisions the console, API, and MySQL:
```bash
docker compose -f deploy/compose/docker-compose.yml up --build
```
This command mounts persistent state to `tmp/data/` and wires environment variables to align with local defaults. Use `docker compose ... down` to stop the stack.

### CI Considerations
The GitHub Actions workflow compiles sources, packages runnable JARs, runs migrations, and executes a persistence smoke test. Container build steps are wrapped in a registry availability check to avoid transient Docker Hub outages.

## Security Notes
- Rotate database credentials regularly and store them in a secrets manager or CI-provided secret store.
- Enforce HTTPS/TLS termination in front of the API service when deploying to production.
- Review [docs/security.md](../docs/security.md) for threat modeling and recommended mitigations.

