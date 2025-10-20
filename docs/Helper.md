Perfect 👍 here’s your **simplified GitHub-ready README** —
only essential **commands + brief descriptions**, cleanly formatted for easy copy-paste.

---

# Banking System — Local Build & Test Helper

Minimal guide to build, run, and test the **Banking System** (Java + MySQL) locally on **Windows 11 (PowerShell)**.

---

## ⚙️ Build the System

**1️. Set Environment Variables**

```powershell
$env:BANKING_STORAGE_MODE = "jdbc"
$env:BANKING_JDBC_URL = "jdbc:mysql://localhost:3306/banking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:BANKING_DB_USER = "bank_user"
$env:BANKING_DB_PASSWORD = "ChangeMe123!"
```

> Configures JDBC mode and credentials.

---

**2️ Clean & Prepare Build Directory**

```powershell
Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path build\classes | Out-Null
```

**3️ Generate Source List**

```powershell
Get-ChildItem -Path src\main\java -Filter *.java -Recurse |
  ForEach-Object { $_.FullName } | Out-File -Encoding ascii sources.txt
```

**4️ Compile Java Sources**

```powershell
cmd /c "javac -d build\classes @sources.txt"
```

**5️ Verify Class Files**

```powershell
Get-ChildItem build\classes -Recurse | Select-Object -First 10
```

**6️ Run Database Migrations**

```powershell
cmd /c "java -cp ""build\classes;lib\mysql\mysql-connector-j-8.0.33.jar"" banking.persistence.repository.DatabaseMigrationCli"
```

**7️ Launch Console Application**

```powershell
cmd /c "java -cp ""build\classes;lib\mysql\mysql-connector-j-8.0.33.jar"" banking.BankingApplication"
```

---

## 🔄 PR Merge (Local Integration)

```powershell
$PR = x
$INTEG = "integ/pr$PR"

git fetch --all --prune
git switch main
git reset --hard origin/main
git switch -c "$INTEG" origin/main
git merge "origin/pr/$PR"

/* Resolve the conflict*/

git add -A
git commit -m "merge pr/$PR into $INTEG (manual resolution)"
git switch main
git merge --ff-only "$INTEG"
git push origin main
```

---

## 🧮 Database Check

**Interactive**

```powershell
mysql -u bank_user -p banking
```

```sql
SHOW TABLES;
SELECT COUNT(*) AS total FROM bank_accounts;
SELECT * FROM bank_accounts LIMIT 5;
EXIT;
```

**All-in-one**

```powershell
mysql -u bank_user -p -e "
USE banking;
SHOW TABLES;
SELECT COUNT(*) AS total_accounts FROM bank_accounts;
SELECT COUNT(*) AS total_transactions FROM bank_transactions;
SELECT account_number,user_name,account_type,balance,creation_date
FROM bank_accounts ORDER BY account_number DESC LIMIT 5;
SELECT * FROM bank_transactions LIMIT 5;"
```

---

## 🌐 HTTP API Verification

**Start Server (new PowerShell)**

```powershell
cmd /c "java -cp ""build\classes;lib\mysql\mysql-connector-j-8.0.33.jar"" banking.api.ApiApplication"
```

> Expected: `HTTP API listening on port 8080`

---

**Client (another PowerShell window)**

```powershell
# Login and get token
$t = (Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/login" `
      -Body "username=admin&password=admin123!").token
$h = @{ Authorization = "Bearer $t" }

# Health
Invoke-RestMethod -Uri "http://localhost:8080/health" -Headers $h

# List accounts
Invoke-RestMethod -Uri "http://localhost:8080/accounts" -Headers $h

# Create new account
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/accounts" -Headers $h `
  -ContentType "application/x-www-form-urlencoded" -Body "name=Abhi&type=savings&deposit=1500"

# Metrics
Invoke-RestMethod -Uri "http://localhost:8080/metrics" -Headers $h
```

> Stop API with **Ctrl + C** in the server window.

---