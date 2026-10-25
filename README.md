# ChainLance: Blockchain-Escrow Freelance Marketplace

ChainLance is a decentralized escrow marketplace combining an off-chain Spring Boot workflow engine with Ethereum smart contracts. Clients post projects, freelancers submit proposals, and both parties negotiate milestone allocations in Indian Rupees (INR). When terms are finalized, the agreement is frozen with an immutable cryptographic snapshot and anchored to an on-chain escrow contract on Ethereum (Sepolia or local Hardhat).

---

## 1. System Architecture & Authority Split

ChainLance enforces a strict separation of authority:
* **Smart Contracts (`contracts/`)**: Authoritative for all financial state transitions, escrow custody, deposits, payouts, agreed mutual settlements, and dispute resolutions. Native ETH only.
* **MySQL & Spring Boot Backend (`backend/`)**: Authoritative for all workflow state transitions, authentication, project listings, proposal negotiations, milestone planning, work submissions, deadline extension approvals, evidence threads, and two-way reviews.
* **Non-Custodial Client & MetaMask (`frontend/`)**: All transactions are signed directly by the user's wallet via MetaMask. The backend holds zero private keys, never broadcasts transactions, and operates purely as a read-only chain verifier using Web3j.

---

## 2. Prerequisites

* **Operating System**: Windows 10/11 (PowerShell)
* **Java**: OpenJDK 17 LTS
* **Node.js**: v20+ LTS & npm
* **Database**: MySQL 8.0+ running on `localhost:3306`
* **Wallet**: MetaMask browser extension (for frontend interactions)
* **Build Tools**: Apache Maven 3.9+

---

## 3. Quick Start (Windows PowerShell)

### Step 1: Database Setup
Ensure MySQL is running on port 3306, and create the application and test databases:
```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS chainlance; CREATE DATABASE IF NOT EXISTS chainlance_test;"
```

### Step 2: Backend Configuration & Start
Copy `backend/.env.example` to `backend/.env` and ensure database credentials match your local MySQL:
```powershell
# In backend/.env:
# DB_URL=jdbc:mysql://localhost:3306/chainlance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
# DB_USERNAME=chainlance
# DB_PASSWORD=saisagar
# JWT_SECRET=your-random-32-char-secret...
# CHAIN_ID=11155111
```
Start the backend using the local PowerShell launcher (automatically runs Flyway migrations V1, V2, and V3):
```powershell
powershell -ExecutionPolicy Bypass -File backend\run-local.ps1
```
* Backend starts at `http://localhost:8080`. Actuator health is accessible at `http://localhost:8080/actuator/health`.

### Step 3: Frontend Configuration & Start
In a new PowerShell window:
```powershell
cd frontend
npm install
npm run dev
```
* Frontend starts at `http://localhost:5173`. Open your browser to explore the marketplace.

---

## 4. Test Suites & Verification

Every layer contains comprehensive unit and end-to-end integration tests:

### 1. Smart Contracts (Hardhat)
```powershell
cd contracts
npm test
npx hardhat coverage
```
* **Result**: 49 tests passing, 0 failing.
* **Coverage**: 98.53% Statements, 99.01% Lines (`ChainLanceEscrow.sol` 100% Statements / Lines).

### 2. Backend (Spring Boot + MySQL)
```powershell
$env:TEST_DB_URL="jdbc:mysql://localhost:3306/chainlance_test?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
$env:TEST_DB_USERNAME="chainlance"
$env:TEST_DB_PASSWORD="saisagar"
cd backend
mvn clean test
```
* **Result**: 163 tests passing, 0 failures, 0 skipped.

### 3. Frontend (Vitest & TypeScript)
```powershell
cd frontend
npm run typecheck
npm test
npm run build
```
* **Result**: 31 tests passing across 5 test suites; TypeScript clean build (0 errors).

### 4. End-to-End Automated Local Verification
With the backend running on port 8080:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\e2e-local.ps1
```
* **Result**: Exercises the complete journey: Auth -> Project creation & milestone planning -> Proposal submission -> Counter-offer negotiation -> Agreement snapshot & integrity check -> Real EIP-191 personal_sign wallet verification -> Proportional milestone scaling (0 remainder loss) -> Post-funding escrow workflow verification.

---

## 5. Guide for Deploying to Sepolia Testnet (Owner Action)

Follow these exact PowerShell steps when you are ready to deploy to Sepolia:

1. **Configure Contract Secrets**:
   Edit `contracts/.env`:
   ```ini
   SEPOLIA_RPC_URL="https://sepolia.infura.io/v3/YOUR_INFURA_KEY"
   DEPLOYER_PRIVATE_KEY="YOUR_SEPOLIA_DEPLOYER_PRIVATE_KEY"
   ETHERSCAN_API_KEY="YOUR_ETHERSCAN_KEY"
   ADMIN_ADDRESS="YOUR_PLATFORM_ADMIN_PUBLIC_WALLET_ADDRESS"
   ```

2. **Deploy Factory to Sepolia**:
   ```powershell
   cd contracts
   npx hardhat run scripts/deploy.js --network sepolia
   ```
   Note the printed `ChainLanceFactory deployed to: 0x...` address.

3. **Configure Backend Environment**:
   In `backend/.env`:
   ```ini
   CHAIN_ID=11155111
   SEPOLIA_RPC_URL=https://sepolia.infura.io/v3/YOUR_INFURA_KEY
   FACTORY_ADDRESS=0x... # (From step 2)
   ADMIN_WALLET_ADDRESS=0x... # (Your public admin address)
   REQUIRED_CONFIRMATIONS=1
   ```
   Restart the backend via `backend\run-local.ps1`.

4. **Smoke-Test with MetaMask**:
   - Connect MetaMask to Sepolia network.
   - Accept a proposal in ChainLance to finalize an agreement.
   - Click "Deploy Escrow on Sepolia", confirm MetaMask transaction.
   - Click "Deposit ETH & Fund", confirm MetaMask transaction.
   - Milestone 1 is verified on-chain by the backend and transitions to `IN_PROGRESS`.
