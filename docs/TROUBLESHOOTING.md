# ChainLance Troubleshooting & Operational Guide

This document captures solutions for common setup, database, PowerShell, and blockchain integration issues.

---

## 1. Windows PowerShell Pitfalls

### Ampersands in JDBC Connection Strings
PowerShell treats `&` as a background execution operator. If you write:
```powershell
# BAD: PowerShell treats & as a job backgrounding command
mvn test -Dspring.datasource.url=jdbc:mysql://localhost:3306/chainlance?useSSL=false&allowPublicKeyRetrieval=true
```
It fails with a syntax error.
**Solution**: Always pass URLs via environment variables or wrapped in escaped quotes:
```powershell
$env:TEST_DB_URL="jdbc:mysql://localhost:3306/chainlance_test?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
mvn test
```

### Command Chaining
PowerShell does not support `&&` in older versions. Always chain commands using `;`:
```powershell
# Use ';' to chain
cd backend; mvn clean test
```

---

## 2. MySQL & Flyway Migration Issues

### Access Denied for User `chainlance`
* If running tests fails with `Access denied for user 'chainlance'@'localhost' (using password: NO)`, verify that `$env:TEST_DB_PASSWORD` is set to your MySQL user's password.
* Set process environment variables:
  ```powershell
  $env:TEST_DB_USERNAME="chainlance"
  $env:TEST_DB_PASSWORD="your-mysql-password"
  ```

### Flyway Validation Errors
* ChainLance uses `spring.jpa.hibernate.ddl-auto=validate`. Flyway migrations are strictly append-only.
* **Never edit `V1__init.sql` or `V2__marketplace_core.sql`**. Any schema adjustments must be introduced in `V3__...sql`, `V4__...sql`, etc.
* If a migration fails mid-way in development, repair schema history:
  ```powershell
  mysql -u root -p -e "DELETE FROM chainlance.flyway_schema_history WHERE success = 0;"
  ```

---

## 3. Blockchain & MetaMask Integration

### MetaMask Network Mismatch
* If the frontend displays `MetaMask is on chain X, but chain Y is required`, click the **"Switch Network"** button in the Escrow Manager.
* The frontend initiates an EIP-3326 `wallet_switchEthereumChain` request. Ensure your MetaMask contains Sepolia (Chain ID 11155111) or Hardhat Local (Chain ID 31337).

### Confirmation Delays on Sepolia
* The backend enforces `chainlance.chain.required-confirmations` (default `1`). On Ethereum Sepolia, new blocks take ~12 seconds.
* If transaction verification is pending, click the **"Sync / Reconcile On-chain State"** button in the agreement escrow panel to refresh on-chain receipts.

---

## 4. Frontend & Dev Server Ports

* The Spring Boot backend restricts CORS origins strictly to `CORS_ALLOWED_ORIGINS` (default: `http://localhost:5173`).
* Vite's config in `frontend/vite.config.ts` sets `strictPort: true` so the dev server always runs on port 5173 and never silently jumps to another port.
