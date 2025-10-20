# Windows 11 Local Environment Setup

This guide walks through preparing a Windows 11 workstation to build, run, and operate the Banking System project entirely from scratch. Follow the steps in order; each section builds on the previous one.

## 1. Prerequisites
- Windows 11 (21H2 or later) with administrator privileges.
- Reliable internet connection for downloading tooling and dependencies.
- At least 4 GB of free disk space.

> 💡 All command snippets assume **PowerShell** running as an administrator unless stated otherwise.

## 2. Install Required Tooling

### 2.1 Install Git
```powershell
winget install --id Git.Git -e --source winget
```
Confirm installation:
```powershell
git --version
```

### 2.2 Install Java Development Kit 17
The project targets Java 17. Install the Temurin distribution via Windows Package Manager:
```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e --source winget
```
Verify:
```powershell
java -version
javac -version
```

### 2.3 Install cURL (optional but handy for API tests)
```powershell
winget install --id curl.curl -e --source winget
```

### 2.4 Install MySQL Community Server
1. Install via the Windows Package Manager:
   ```powershell
   winget install --id Oracle.MySQL -e --source winget
   ```
   If the command prompts to open the MySQL Installer UI, complete the wizard using the **Developer Default** configuration.
2. Configure the root password when prompted. Record it securely.
3. Ensure MySQL is running as a Windows service (the installer does this by default).
4. Open a MySQL shell and create an application database and user for the banking service:
   ```powershell
   "CREATE DATABASE banking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | mysql -u root -p
   "CREATE USER 'bank_user'@'localhost' IDENTIFIED BY 'ChangeMe123!';" | mysql -u root -p
   "GRANT ALL PRIVILEGES ON banking.* TO 'bank_user'@'localhost';" | mysql -u root -p
   "FLUSH PRIVILEGES;" | mysql -u root -p
   ```
   > ℹ️ Passwords shown here are examples; choose unique credentials for your environment. The application connects to this schema beginning with the current release.

## 3. Clone the Repository Over HTTPS
Choose a directory (e.g., `C:\dev`) and run:
```powershell
cd C:\dev
git clone https://github.com/<your-org>/Banking-System.git
cd Banking-System
```

If your organization uses Git over HTTPS with personal access tokens, replace `<your-org>` and provide credentials when prompted.

## 4. Prepare Environment Variables
Configure the runtime to use the MySQL instance created earlier:
```powershell
$env:BANKING_STORAGE_MODE = "jdbc"
$env:BANKING_JDBC_URL = "jdbc:mysql://localhost:3306/banking?useSSL=true&requireSSL=false&serverTimezone=UTC"
$env:BANKING_DB_USER = "bank_user"
$env:BANKING_DB_PASSWORD = "ChangeMe123!"
[Environment]::SetEnvironmentVariable("BANKING_STORAGE_MODE", $env:BANKING_STORAGE_MODE, "User")
[Environment]::SetEnvironmentVariable("BANKING_JDBC_URL", $env:BANKING_JDBC_URL, "User")
[Environment]::SetEnvironmentVariable("BANKING_DB_USER", $env:BANKING_DB_USER, "User")
[Environment]::SetEnvironmentVariable("BANKING_DB_PASSWORD", $env:BANKING_DB_PASSWORD, "User")
```
Persisting these values ensures new PowerShell sessions inherit the configuration. If you need to test the legacy snapshot flow, unset `BANKING_STORAGE_MODE` and set `BANKING_DATA_PATH` instead.

## 5. Compile the Project
From the repository root:
```powershell
New-Item -ItemType Directory -Force -Path build\classes | Out-Null
Get-ChildItem -Path src -Filter *.java -Recurse | ForEach-Object { $_.FullName } | Out-File -Encoding ascii sources.txt
javac -d build\classes @sources.txt
```
> ✅ Compilation should finish without errors and populate `build\classes` with `.class` files.

## 6. Run Database Migrations
The migration helper executes JDBC migrations so the schema matches the application version and records progress in `bank_schema_migrations`.
```powershell
bash deploy\scripts\run-migrations.sh
```
- If prompted about executing scripts, allow PowerShell to run the bundled Bash via Git.
- The script reads `BANKING_JDBC_URL`, `BANKING_DB_USER`, and `BANKING_DB_PASSWORD`; verify they are set before running.

## 7. Launch the Console Application
```powershell
java -cp build\classes banking.BankingApplication
```
- Follow the on-screen menu to open accounts, post transactions, and exit cleanly (option **7**) so state is saved.

## 8. Launch and Exercise the HTTP API
Start the HTTP server in a new PowerShell window:
```powershell
java -cp build\classes banking.api.ApiApplication
```
Request a short-lived operator token and interact with the API from another window (every request must include the bearer token header):
```powershell
$env:BANKING_TOKEN = (curl -Method Post -Body "username=admin&password=admin123!" http://localhost:8080/auth/login | ConvertFrom-Json).token
curl -H "Authorization: Bearer $env:BANKING_TOKEN" -X POST "http://localhost:8080/accounts" -d "name=Grace&type=savings&deposit=1000"
curl -H "Authorization: Bearer $env:BANKING_TOKEN" "http://localhost:8080/accounts"
curl -H "Authorization: Bearer $env:BANKING_TOKEN" "http://localhost:8080/accounts/<ACCOUNT_NUMBER>"
curl -H "Authorization: Bearer $env:BANKING_TOKEN" -X PUT "http://localhost:8080/accounts/<ACCOUNT_NUMBER>" -d "userName=Grace%20Hopper"
curl -H "Authorization: Bearer $env:BANKING_TOKEN" -X DELETE "http://localhost:8080/accounts/<ACCOUNT_NUMBER>"
curl -H "Authorization: Bearer $env:BANKING_TOKEN" "http://localhost:8080/metrics"
```
Use `Ctrl+C` in the API window to trigger a graceful shutdown and final persistence cycle once testing completes.

## 9. Optional: Docker & Container Orchestration
If you plan to test containerized workflows:
1. Install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) and enable WSL 2 integration.
2. Build images locally:
   ```powershell
   docker compose -f deploy\compose\docker-compose.yml build
   ```
3. Run the stack:
   ```powershell
   docker compose -f deploy\compose\docker-compose.yml up
   ```
4. Tear down with `docker compose ... down` when finished.

## 10. Troubleshooting Tips
- **`javac` not found:** Re-open PowerShell after installing the JDK so the PATH updates, or add `%ProgramFiles%\Eclipse Adoptium\jdk-17\bin` manually.
- **`bash` command missing:** Ensure Git for Windows installed its bundled GNU tools; re-run the installer and enable the “Git Bash utilities” option if necessary.
- **MySQL authentication errors:** Reset the root password via the MySQL Installer or run `mysqladmin -u root password <newPassword>`, then update `BANKING_DB_PASSWORD` to match.
- **Port conflicts on 8080:** Stop other services (like IIS Express) or update `ApiApplication.HTTP_PORT` before starting the API server.

You now have a fully configured Windows 11 environment capable of building, testing, and operating the Banking System locally.
