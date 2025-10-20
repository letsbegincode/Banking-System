# Environment Setup

Follow this guide to prepare a development workstation, compile the codebase, and run the Banking System locally.

## Prerequisites
- **Java Development Kit 17** (Temurin or compatible distribution)
- **Git** for cloning the repository
- **Optional:** Docker (for container-based workflows), MySQL client utilities (for JDBC deployments)

> Windows users can reference the detailed [setup checklist](../docs/setup-windows.md) for IDE tooling, PATH configuration, and helper scripts.

## Clone the Repository
```bash
git clone https://github.com/alphaZytx/Banking-System.git
cd Banking-System
```

## Build from Source
The project uses vanilla `javac` compilation to keep the toolchain lightweight.
```bash
mkdir -p build/classes
find src -name '*.java' > sources.txt
javac -d build/classes @sources.txt
```
The resulting classes support both the console and API applications.

## Launch the Console Application
```bash
java -cp build/classes banking.BankingApplication
```
The console guides you through creating accounts, performing transactions, and reviewing history. Exit gracefully through the provided menu option to flush persistence state.

## Launch the HTTP API
```bash
java -cp build/classes banking.api.ApiApplication
```
Once the server is running, obtain an operator token and interact with the endpoints:
```bash
TOKEN=$(curl -s -X POST "http://localhost:8080/auth/login" -d "username=admin&password=admin123!" | jq -r .token)
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/accounts"
```
Consult the [Operations & Runbooks](Operations.md) page for additional API workflows and post-start validation steps.

## Environment Configuration
Key environment variables:
- `BANKING_STORAGE_MODE`: `snapshot` (default) or `jdbc`
- `BANKING_DATA_PATH`: File path for snapshot persistence when using the in-memory repository
- `BANKING_JDBC_URL`: JDBC connection string (required for JDBC mode)
- `BANKING_DB_USER` / `BANKING_DB_PASSWORD`: Database credentials
- `CACHE_PROVIDER`: `memory` (default) or `none`
- `CACHE_TTL_SECONDS`, `CACHE_ACCOUNT_TTL_SECONDS`, `CACHE_BALANCE_TTL_SECONDS`: Cache expiration tuning

Example JDBC configuration:
```bash
export BANKING_STORAGE_MODE=jdbc
export BANKING_JDBC_URL="jdbc:mysql://localhost:3306/banking?useSSL=true&serverTimezone=UTC"
export BANKING_DB_USER="bank_user"
export BANKING_DB_PASSWORD="ChangeMe123!"
```

## Database Migrations
Run the built-in migration script before launching the API in JDBC mode:
```bash
bash deploy/scripts/run-migrations.sh
```
The script skips JDBC migrations automatically when `BANKING_JDBC_URL` is unset.

## Container Workflow (Optional)
The repository includes Dockerfiles for the console and API applications and a Docker Compose configuration for local orchestration:
```bash
docker compose -f deploy/compose/docker-compose.yml up --build
```
This command provisions MySQL, builds the application images, and starts the services with compatible environment variables. Tear down with `docker compose ... down` when finished.

