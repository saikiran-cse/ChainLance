# ChainLance System Integration Architecture

This document details the end-to-end integration across Ethereum Smart Contracts, the Spring Boot Web3j backend, and the React MetaMask frontend.

---

## 1. Authority Split & Boundary Model

ChainLance enforces a strict separation between workflow management and financial custody:

```
+-------------------------------------------------------------------------------+
|                             CLIENT / FREELANCER                               |
|                     (React 19 + TypeScript + MetaMask)                        |
+-------------------------------------------------------------------------------+
           |                                                      |
    HTTP / REST (JWT)                                    EIP-1193 Signer
           v                                                      v
+-------------------------------+             +---------------------------------+
|     BACKEND WORKFLOW ENGINE   |             |     ETHEREUM SMART CONTRACTS    |
| (Spring Boot 3.3.5 + MySQL 8) |             |     (Hardhat + Solidity 0.8)    |
|                               |             |                                 |
| - Proposal negotiation        |             | - Factory registry              |
| - Frozen agreement snapshots  |   Web3j     | - Escrow native ETH custody     |
| - Milestone plan & deadlines  |  Read-only  | - Milestone release payouts     |
| - Submissions & revision notes|  RPC Reads  | - Agreed mutual settlements     |
| - Deadline extensions         | ----------> | - Admin dispute disbursements   |
| - Evidence submissions        |             | - Zero-loss invariant balance   |
+-------------------------------+             +---------------------------------+
```

### Key Principles
1. **Zero Private Keys on Server**: The backend holds zero private keys, never broadcasts transactions, and never signs messages.
2. **Read-Only Verification**: The backend verifies on-chain state changes via read-only Web3j RPC calls against receipt logs, checking block confirmations, contract caller, and event topic hashes.
3. **Optimistic Workflow vs. On-Chain Finality**: The UI only reflects financial state changes (e.g. `FUNDED`, `RELEASED`, `SETTLED`, `DISPUTE_RESOLVED`) after the backend has verified the corresponding transaction receipt on-chain.

---

## 2. Cryptographic Project ID & Snapshot Integrity

### 2.1 Agreement Snapshot
When a client accepts a proposal, the backend freezes the complete terms into an immutable canonical JSON snapshot (`agreements.snapshot_json`):
* Includes parties (client, freelancer), pricing (INR & wei), exchange rate, agreed duration, and complete negotiation history.
* The columns `snapshot_json` and `snapshot_hash` are configured with JPA `updatable = false`.
* Every agreement read validates `keccak256(canonicalJson) == snapshot_hash`.

### 2.2 On-Chain Project ID
The escrow contract pins each escrow instance to an opaque 32-byte identifier computed identically in Solidity, Java (Web3j), and TypeScript (ethers):
$$\text{onchainProjectId} = \text{keccak256}(\text{abi.encodePacked}(\text{uint256 agreementId}, \text{bytes32 snapshotHash}))$$

* **Solidity**: `keccak256(abi.encodePacked(agreementId, snapshotHash))`
* **Java**: `OnchainProjectId.compute(agreementId, snapshotHash)` using Web3j `Hash.sha3(...)`
* **TypeScript**: `ethers.solidityPackedKeccak256(['uint256', 'bytes32'], [agreementId, snapshotHash])`

---

## 3. Milestone Proportional Scaling & Zero-Loss Remainder

Milestone allocations are defined off-chain as percentage shares (e.g., 30%, 40%, 30%). When generating agreement milestones:
1. **Indicative INR**: Scaled with `BigDecimal.ROUND_HALF_UP`, with any fractional cent discrepancy attributed to the final milestone.
2. **Wei Precision**: Computed using integer division:
   $$\text{milestoneWei}_i = \left\lfloor \frac{\text{totalWei} \times \text{percentage}_i}{100} \right\rfloor$$
3. **Remainder Conservation Invariant**:
   $$\sum_{i=0}^{N-1} \text{milestoneWei}_i \equiv \text{totalWei}$$
   The remainder ($\text{totalWei} - \sum \text{milestoneWei}$) is allocated to the final milestone, guaranteeing 100.00% exact parity with the smart contract deposit.

---

## 4. Wallet Linking via EIP-191 Personal Sign

To guarantee non-repudiation of client and freelancer wallet addresses:
1. User requests challenge: `POST /api/wallet/challenge` with `{ address }`.
2. Backend generates single-use expiring nonce with standard sign message.
3. User signs in MetaMask via `personal_sign(message, address)`.
4. User submits signature: `POST /api/wallet/verify` with `{ nonce, signature }`.
5. Backend recovers public key via Web3j `Sign.signedPrefixedMessageToKey`, validates checksummed address, and persists `WalletAddress(verifiedAt)`.
6. User links verified wallet to the agreement: `POST /api/agreements/{id}/wallets`.

---

## 5. Milestone & Escrow State Machines

```
                          +------------------------+
                          |   AGREEMENT (DRAFT)    |
                          +------------------------+
                                      |
                         Client accepts proposal
                                      v
                          +------------------------+
                          | AGREEMENT (FINALIZED)  |
                          +------------------------+
                                      |
                        Client deploys & funds escrow
                                      v
                          +------------------------+
                          |   AGREEMENT (FUNDED)   |
                          +------------------------+
                                      |
                     [Per Milestone Execution Lifecycle]
                                      |
                                      +-------------------------------+
                                      |                               |
                                      v                               v
                             approveMilestone()             proposeSettlement()
                                      |                               |
                                      v                               v
                         Milestone: RELEASED             Milestone: SETTLED
                                      |                               |
                                      +---------------+---------------+
                                                      |
                                                      v
                                        openDispute() -> resolveDispute()
                                                      |
                                                      v
                                        Milestone: DISPUTE_RESOLVED
                                                      |
                                      All milestones resolved
                                                      v
                                         +--------------------------+
                                         |  AGREEMENT (COMPLETED)   |
                                         +--------------------------+
                                                      |
                                         Two-way Reviews Submitted
```

### Milestone State Dimensions
* **`workStatus` (MySQL)**:
  - `PENDING` $\to$ Waiting for previous milestone completion.
  - `IN_PROGRESS` $\to$ Active milestone; freelancer can submit work or request extension.
  - `SUBMITTED` $\to$ Work under client review.
  - `REVISION_REQUESTED` $\to$ Client requested revisions with note.
  - `APPROVED` $\to$ Client accepted submission.
  - `CLOSED` $\to$ Milestone resolved via settlement or dispute.
* **`chainStatus` (Contract & Reconciled in MySQL)**:
  - `OPEN` $\to$ Allocated funds locked in escrow.
  - `RELEASED` $\to$ 100% allocation transferred to freelancer.
  - `SETTLED` $\to$ Agreed split transferred to freelancer and client.
  - `DISPUTED` $\to$ Milestone frozen for admin arbitration.
  - `DISPUTE_RESOLVED` $\to$ Admin split executed on-chain.
