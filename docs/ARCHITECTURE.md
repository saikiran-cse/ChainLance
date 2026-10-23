# ChainLance — Architecture, Contract Design and Checklist (Phase 1)

## 1. Architecture

```
React (Vite) ──Axios/JWT──> Spring Boot REST API ──JPA──> MySQL (Flyway)
     │                              │
     │ ethers.js + MetaMask         │ web3 read-only (Sepolia RPC, server-side secret)
     ▼                              ▼
  ChainLanceFactory ──creates──> ChainLanceEscrow (one per agreement)   [Ethereum Sepolia]
```

* Users sign their own transactions in MetaMask. The backend never holds a user or admin private key.
* **Authority split.** The contract is authoritative for funds and payouts. MySQL is authoritative for workflow (proposals, negotiation, deadlines, submissions, evidence).
* **Admin.** Spring Security gates the admin UI (role ADMIN). Solidity separately gates `resolveDispute` to the admin *wallet* fixed in each escrow at creation. The admin signs in MetaMask. A DB role alone cannot move funds.
* **Backend web3 library:** web3j (Java) for receipt/log reads. Chain ID 11155111 is checked on every verification.
* **Deadlines:** application-level only (MySQL). The contract has no deadline logic. Late work is handled by the settlement path. This is deliberate: it keeps the contract small and avoids on-chain deadline mutation.

## 2. Contract design (implemented in `contracts/`)

Design chosen: **factory + one escrow per agreement** (non-upgradeable, no proxies). Trade-offs: isolated funds per project and simple audits, at the cost of one deployment per agreement (higher gas than a single shared contract with mappings; acceptable on testnet).

Money paths, each milestone resolved exactly once:

| Path | Who | Result |
|---|---|---|
| `approveMilestone(i)` | client | full allocation to freelancer |
| `proposeSettlement(i,f,c)` + `acceptSettlement(i,f,c)` | one party proposes, the *other* accepts the same amounts | `f` to freelancer, `c` to client, `f+c == allocation` |
| `openDispute(i,reasonHash)` then `resolveDispute(i,f,c,resolutionHash)` | party opens, **admin** resolves | milestone frozen until resolved; `f+c == allocation` |

Refunds exist only as the client-side of a settlement or resolution (a full refund is `f = 0`). There is no unilateral client withdrawal, so the client can never recover funds already paid.

Cancellation: there is no separate cancel function. Before funding nothing is at risk (abandon the escrow). After funding, each unresolved milestone is closed by mutual settlement or by dispute. A freelancer who disappears or a client who never approves is handled by opening a dispute. Disputes freeze only the disputed milestone; others continue.

Admin power is limited to splitting one disputed milestone's own allocation. No withdraw function exists. The admin cannot be a client or freelancer of the same escrow. The factory owner (the admin) changes by two-step transfer and ownership cannot be renounced. Rotation affects only escrows created afterwards. Losing the admin key leaves disputed milestones of existing escrows unresolvable (documented limitation).

Events: `Funded`, `MilestoneReleased`, `SettlementProposed`, `SettlementWithdrawn`, `MilestoneSettled`, `DisputeOpened`, `DisputeResolved`, `EscrowCompleted`, and factory `EscrowCreated`.

Agreement link: `projectId = keccak256(agreement id ‖ snapshot hash)`. The backend verifies that the on-chain allocations equal the MySQL snapshot before marking an agreement FUNDED.

On-chain milestone state vs. off-chain work state: contract statuses are Open, Released, Settled, Disputed, DisputeResolved. Pending, In progress, Submitted, Revision requested exist only in MySQL. The UI shows "Paid" only from a confirmed, verified transaction.

## 3. Database (MySQL, Flyway)

Amounts: `DECIMAL(38,0)` wei for ETH, `DECIMAL(15,2)` for INR (indicative). No floats.

| Table | Key columns / constraints |
|---|---|
| users | id, email UNIQUE, password_hash, display_name, role (CLIENT, FREELANCER, ADMIN), bio, created_at |
| wallet_addresses | id, user_id FK, address UNIQUE, verified_at |
| wallet_challenges | id, user_id FK, nonce UNIQUE, expires_at, used_at (nonce single-use) |
| projects | id, client_id FK, title, description, category, skills, budget_inr, deadline, status, timestamps |
| project_milestone_plans | id, project_id FK, position, description, allocation_inr |
| proposals | id, project_id FK, freelancer_id FK, message, price_inr, duration_days, portfolio_links, status; UNIQUE(project_id, freelancer_id) |
| negotiation_messages | id, proposal_id FK, sender_id FK, message, proposed_terms_json, created_at |
| agreements | id, proposal_id UNIQUE FK, total_wei, status (DRAFT, FINALIZED, FUNDED, COMPLETED, CANCELLED), client_finalized_at, freelancer_finalized_at, snapshot_json, snapshot_hash, escrow_address, chain_id |
| milestones | id, agreement_id FK, position, description, acceptance_criteria, amount_wei, original_deadline, effective_deadline, work_status, chain_status; UNIQUE(agreement_id, position) |
| submissions | id, milestone_id FK, description, links, submitted_at, is_late, review_status, revision_reason |
| deadline_extension_requests | id, milestone_id FK, current_deadline, requested_deadline, reason, status, requested_at, decided_at |
| settlement_proposals | id, milestone_id FK, proposer_id FK, freelancer_wei, client_wei, status |
| disputes | id, milestone_id FK, opened_by FK, reason, status, admin_decision, admin_explanation, resolved_tx_id |
| dispute_responses | id, dispute_id FK, user_id FK, response, evidence_links |
| blockchain_transactions | id, tx_hash UNIQUE, type (CREATE_ESCROW, FUND, APPROVE, SETTLE, DISPUTE, RESOLVE), status (PENDING, CONFIRMED, FAILED), agreement_id FK, milestone_id FK, from_address, block_number, verified_at, error |
| reviews | id, agreement_id FK, reviewer_id FK, reviewee_id FK, rating 1-5, comment; UNIQUE(agreement_id, reviewer_id) |

Relationships: user 1—N projects, project 1—N proposals, proposal 1—N negotiation_messages, proposal 1—1 agreement, agreement 1—N milestones, milestone 1—N submissions / extension requests / settlement proposals / disputes, agreement 1—N blockchain_transactions.

## 4. REST API (planned, `/api`)

| Area | Endpoints |
|---|---|
| Auth | POST /auth/register, POST /auth/login, GET /users/me, PUT /users/me |
| Wallet | POST /wallet/challenge, POST /wallet/verify (signature + single-use nonce) |
| Projects | GET /projects (filters), POST /projects, GET/PUT /projects/{id}, POST /projects/{id}/publish, POST /projects/{id}/close |
| Proposals | POST /projects/{id}/proposals, GET /projects/{id}/proposals (owner), GET /proposals/mine, POST /proposals/{id}/shortlist, /accept, /reject |
| Negotiation | GET/POST /proposals/{id}/negotiation, POST /agreements/{id}/counter, POST /agreements/{id}/finalize |
| Agreement | GET /agreements/{id}, GET /agreements/mine |
| Chain | POST /agreements/{id}/escrow/register (factory tx hash), POST /transactions/verify {txHash}, GET /transactions/mine, POST /agreements/{id}/reconcile |
| Milestones | GET /agreements/{id}/milestones, POST /milestones/{id}/start |
| Submissions | POST /milestones/{id}/submissions, POST /submissions/{id}/approve (records tx), POST /submissions/{id}/request-revision |
| Deadlines | POST /milestones/{id}/extensions, POST /extensions/{id}/approve, /reject |
| Settlement | POST /milestones/{id}/settlements, POST /settlements/{id}/accept, /withdraw |
| Disputes | POST /milestones/{id}/disputes, POST /disputes/{id}/responses, GET /disputes/mine |
| Admin | GET /admin/disputes, GET /admin/disputes/{id}, POST /admin/disputes/{id}/decision, POST /admin/disputes/{id}/resolution-tx |
| Reviews | POST /agreements/{id}/reviews, GET /users/{id}/reviews |

All amounts and statuses sent by the browser are re-derived or re-verified server-side. Money-state changes occur only after the receipt and event checks in section 13 of the spec succeed. Verification is idempotent (unique `tx_hash`) and reconciliation re-reads the contract to repair MySQL.

## 5. Checklist

- [x] Phase 1 Architecture (this document)
- [x] Phase 2 Contracts written: factory, escrow, reentrancy mock, deploy script, tests
- [ ] Phase 2 Contracts compiled and tests run (**needs your machine**, see below)
- [ ] Phase 3 Backend: next file `backend/pom.xml`, then Flyway `V1__init.sql`
- [ ] Phase 4 Frontend
- [ ] Phase 5 Blockchain integration and Sepolia deployment
- [ ] Phase 6 Integration testing
- [ ] Phase 7 README, deployment, troubleshooting

## 6. Run Phase 2 (Windows PowerShell)

```powershell
node -v          # use Node 20 or 22 LTS
cd chainlance\contracts
npm install
npx hardhat compile
npx hardhat test
```

Local deploy (two terminals):

```powershell
npx hardhat node                       # terminal 1
npx hardhat run scripts/deploy.js --network localhost   # terminal 2
```

Sepolia deploy: copy `.env.example` to `.env`, fill in values (use a throwaway deployer wallet), then `npx hardhat run scripts/deploy.js --network sepolia`.
