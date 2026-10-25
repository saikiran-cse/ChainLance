# ChainLance: Zero-Cost Academic Cloud Deployment Guide

This guide provides end-to-end instructions for deploying all four tiers of **ChainLance** completely free of charge for academic demonstration and capstone evaluation:

| Tier | Component | Free Cloud Provider | Cost | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **1. Blockchain** | Solidity Smart Contracts | **Ethereum Sepolia Testnet** | \$0.00 | Free testnet faucet ETH; Alchemy/Infura RPC |
| **2. Database** | MySQL 8.0 Database | **TiDB Cloud Serverless** or **Aiven** | \$0.00 | MySQL 8.0 protocol, no credit card required |
| **3. Backend** | Spring Boot 3.3.4 (Java 17) | **Render.com** (Web Service) | \$0.00 | Free 512MB RAM tier, Docker container |
| **4. Frontend** | React 19 + Vite + Tailwind | **Vercel** | \$0.00 | Free Hobby tier, global edge CDN, HTTPS |

---

## Prerequisites

1. **MetaMask Wallet**: Installed in your browser with the Sepolia testnet enabled.
2. **GitHub Account**: Your repository containing the latest commits (branch `stage-5-contracts` or merged into `main`).
3. **Accounts**:
   - [Alchemy](https://www.alchemy.com/) or [Infura](https://www.infura.io/) (free developer account for Sepolia RPC).
   - [TiDB Cloud](https://tidbcloud.com/) or [Aiven](https://aiven.io/) (free MySQL cloud database).
   - [Render](https://render.com/) (free container/web hosting).
   - [Vercel](https://vercel.com/) (free frontend hosting).

---

## Step 1: Deploy Smart Contracts to Sepolia Testnet

### 1.1 Obtain Free Sepolia ETH
Get free testnet ETH into your MetaMask wallet from any of the following faucets:
- **Google Cloud Web3 Faucet**: [cloud.google.com/application/web3/faucet/ethereum/sepolia](https://cloud.google.com/application/web3/faucet/ethereum/sepolia) (fastest, requires Google login)
- **Sepolia PoW Faucet**: [sepolia-faucet.pk910.de](https://sepolia-faucet.pk910.de/)
- **Alchemy Sepolia Faucet**: [sepoliafaucet.com](https://sepoliafaucet.com/)

### 1.2 Get a Free Sepolia RPC Endpoint
1. Go to [Alchemy](https://dashboard.alchemy.com/), create an app on **Ethereum Sepolia**.
2. Copy the HTTPS RPC URL (e.g., `https://eth-sepolia.g.alchemy.com/v2/YOUR_API_KEY`).
   *(Alternatively, a public testnet RPC like `https://ethereum-sepolia-rpc.publicnode.com` can be used).*

### 1.3 Configure and Deploy
1. In `contracts/`, create or update `.env`:
   ```bash
   SEPOLIA_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/YOUR_API_KEY
   DEPLOYER_PRIVATE_KEY=your_metamask_private_key_without_0x_prefix
   ADMIN_ADDRESS=your_metamask_public_address
   ```
2. Run the deployment script:
   ```powershell
   cd contracts
   npx hardhat run scripts/deploy.js --network sepolia
   ```
3. The script outputs:
   ```text
   Network: sepolia (chainId 11155111)
   Deployer: 0x...
   Admin:    0x...
   ChainLanceFactory deployed to: 0xYourFactoryAddressHere
   Saved deployments/sepolia.json
   ```
4. **Save these two values**:
   - `FACTORY_ADDRESS`: `0xYourFactoryAddressHere`
   - `ADMIN_WALLET_ADDRESS`: `0x...` (your MetaMask public address)

---

## Step 2: Set Up Free Cloud MySQL Database (TiDB Cloud)

ChainLance uses standard MySQL 8.0 features (Flyway migrations, InnoDB, `utf8mb4_0900_ai_ci`, stored generated columns). TiDB Cloud Serverless is fully MySQL 8 wire-compatible and 100% free forever without credit card.

1. Go to [tidbcloud.com](https://tidbcloud.com/) and register a free account.
2. Click **Create Cluster** &rarr; Select **Serverless (Free)**.
3. Choose your nearest region (e.g., AWS us-east-1 or ap-southeast-1) and name the cluster `chainlance`.
4. Click **Create**.
5. Once created, click **Connect** &rarr; select **General / JDBC**:
   - TiDB will generate your:
     - `Host` (e.g., `gateway01.us-east-1.prod.aws.tidbcloud.com`)
     - `Port` (usually `4000`)
     - `User` (e.g., `xxxx.root`)
     - `Password` (your cluster password)
     - `Database` (set to `chainlance` or `test`)
6. Construct your Spring Boot JDBC connection string:
   ```text
   jdbc:mysql://<HOST>:<PORT>/chainlance?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=true&serverTimezone=UTC
   ```
   *(Note: Flyway migrations `V1`, `V2`, `V3` will automatically create all tables on first startup).*

---

## Step 3: Deploy Backend on Render (Free Web Service)

Render runs containerized apps using the included multi-stage `backend/Dockerfile`.

### 3.1 Push Code to GitHub
Make sure your latest code and commits are pushed to GitHub:
```powershell
git push origin stage-5-contracts
```
*(Or merge `stage-5-contracts` into `main` and push `main`).*

### 3.2 Create Web Service in Render
1. Go to [dashboard.render.com](https://dashboard.render.com/) and click **New +** &rarr; **Web Service**.
2. Connect your GitHub repository (`saikiran-cse/ChainLance`).
3. Fill in the service configuration:
   - **Name**: `chainlance-api`
   - **Region**: Choose closest to your database (e.g., Oregon or Frankfurt)
   - **Branch**: `stage-5-contracts` (or `main`)
   - **Root Directory**: `backend`
   - **Runtime**: `Docker` (Render automatically detects `Dockerfile`)
   - **Instance Type**: `Free` (512 MB RAM, 0.1 CPU)
4. Add **Environment Variables**:

| Variable | Value | Description |
| :--- | :--- | :--- |
| `DB_URL` | `jdbc:mysql://<HOST>:<PORT>/chainlance?...` | From Step 2 |
| `DB_USERNAME` | `<YOUR_DB_USER>` | From Step 2 |
| `DB_PASSWORD` | `<YOUR_DB_PASSWORD>` | From Step 2 |
| `JWT_SECRET` | `4f8a9b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0` | At least 32 random characters |
| `CHAIN_ID` | `11155111` | Sepolia Chain ID |
| `SEPOLIA_RPC_URL` | `https://eth-sepolia.g.alchemy.com/v2/...` | From Step 1.2 |
| `FACTORY_ADDRESS` | `0xYourFactoryAddressHere` | From Step 1.3 |
| `ADMIN_WALLET_ADDRESS` | `0xYourMetaMaskAddress` | From Step 1.3 |
| `ADMIN_EMAIL` | `admin@chainlance.local` | Platform bootstrap admin |
| `ADMIN_PASSWORD` | `YourAdminStrongPassword123!` | Platform admin password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | *(You will append your Vercel URL here in Step 4)* |

5. Under **Advanced**, set **Health Check Path** to:
   ```text
   /actuator/health
   ```
6. Click **Deploy Web Service**.
7. Once deployed, test health in your browser:
   ```text
   https://chainlance-api.onrender.com/actuator/health
   ```
   Expected response: `{"status":"UP"}`.
8. Copy your Render service URL: `https://chainlance-api.onrender.com`.

---

## Step 4: Deploy Frontend on Vercel (Free CDN)

### 4.1 Import Project in Vercel
1. Go to [vercel.com](https://vercel.com/) and click **Add New...** &rarr; **Project**.
2. Select your `ChainLance` GitHub repository.
3. Configure the project:
   - **Framework Preset**: `Vite`
   - **Root Directory**: Click Edit &rarr; select `frontend`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
4. Add **Environment Variables**:

| Variable | Value |
| :--- | :--- |
| `VITE_API_BASE_URL` | `https://chainlance-api.onrender.com` (no trailing slash) |
| `VITE_USE_MOCKS` | `false` |
| `VITE_EXPECTED_CHAIN_ID` | `11155111` |

5. Click **Deploy**.
6. Vercel will build and assign a production URL (e.g., `https://chainlance.vercel.app`).

### 4.2 Link CORS in Backend
Now that your Vercel URL is live:
1. Return to **Render** &rarr; your `chainlance-api` service &rarr; **Environment**.
2. Update `CORS_ALLOWED_ORIGINS` to include your Vercel URL:
   ```text
   http://localhost:5173,https://chainlance.vercel.app
   ```
3. Render will automatically redeploy the updated environment variables.

---

## Step 5: Verification & Academic Demo Flow

1. Open your live Vercel URL: `https://chainlance.vercel.app`.
2. Connect your **MetaMask** wallet (ensure network is **Sepolia**).
3. **Register two accounts**:
   - **Client Account** (e.g., `client@demo.edu`).
   - **Freelancer Account** (e.g., `freelancer@demo.edu`).
4. **Link Wallets**: Link your client and freelancer MetaMask testnet accounts via EIP-712 / EIP-191 signature challenge.
5. **Project Lifecycle**:
   - Client posts a project with INR milestones.
   - Freelancer submits a proposal.
   - Client accepts the proposal &rarr; agreement snapshot is generated.
   - Client signs `createEscrow` on Sepolia via MetaMask &rarr; backend verifies transaction receipt on-chain.
   - Client funds the escrow using Sepolia ETH.
   - Freelancer submits work for Milestone 1.
   - Client approves & releases payment directly from escrow to the freelancer's wallet.
6. **Live Blockchain Verification**: Click transaction hashes on Etherscan Sepolia (`https://sepolia.etherscan.io/address/0xYourFactoryAddress`) to demonstrate real verifiable blockchain state transitions to your academic examiners.
