# ChainLance REST API Specification

All API endpoints are prefixed with `/api`. Unless explicitly marked **Public**, all endpoints require HTTP Bearer authentication (`Authorization: Bearer <token>`).

---

## 1. Authentication & User Profile

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a new `CLIENT` or `FREELANCER` account |
| `POST` | `/api/auth/login` | Public | Authenticate with email/password and obtain JWT |
| `GET` | `/api/users/me` | Authenticated | Fetch authenticated user profile |
| `PUT` | `/api/users/me` | Authenticated | Update user display name and profile details |

---

## 2. Wallet Management (EIP-191)

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/wallet/challenge` | Authenticated | Request a single-use verification challenge for an address |
| `POST` | `/api/wallet/verify` | Authenticated | Verify personal_sign signature and link wallet to user |
| `GET` | `/api/wallet` | Authenticated | List verified wallet addresses for current user |

---

## 3. Projects & Milestone Planning

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/projects` | `CLIENT` | Create a new project in `DRAFT` status |
| `GET` | `/api/projects` | Authenticated | List `OPEN` projects (paginated) |
| `GET` | `/api/projects/mine` | `CLIENT` | List current client's projects across all statuses |
| `GET` | `/api/projects/{id}` | Authenticated | Get project details (404 for non-participants if not OPEN) |
| `PUT` | `/api/projects/{id}` | `CLIENT` (Owner) | Edit project title, description, budget while in DRAFT/OPEN |
| `PUT` | `/api/projects/{id}/milestone-plan` | `CLIENT` (Owner) | Set or update milestone plan percentage allocations |
| `GET` | `/api/projects/{id}/milestone-plan` | Authenticated | Get defined milestone plan allocations |
| `POST` | `/api/projects/{id}/publish` | `CLIENT` (Owner) | Transition project from `DRAFT` to `OPEN` |
| `POST` | `/api/projects/{id}/close` | `CLIENT` (Owner) | Close an `OPEN` project |
| `POST` | `/api/projects/{id}/cancel` | `CLIENT` (Owner) | Cancel project |

---

## 4. Proposals & Negotiation Offers

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/projects/{projectId}/proposals` | `FREELANCER` | Submit proposal on OPEN project (creates initial Offer #1) |
| `GET` | `/api/projects/{projectId}/proposals` | `CLIENT` (Owner) | View proposals on client's project |
| `GET` | `/api/proposals/my` | `FREELANCER` | View current freelancer's submitted proposals |
| `GET` | `/api/proposals/{id}` | Parties / Admin | View proposal details with full offer history |
| `POST` | `/api/proposals/{id}/shortlist` | `CLIENT` (Owner) | Shortlist proposal |
| `POST` | `/api/proposals/{id}/reject` | `CLIENT` (Owner) | Reject proposal |
| `POST` | `/api/proposals/{id}/withdraw` | `FREELANCER` (Author) | Withdraw proposal |
| `GET` | `/api/proposals/{id}/offers` | Parties / Admin | Get negotiation offer history |
| `POST` | `/api/proposals/{id}/offers` | Parties | Create offer (only when no pending offer exists) |
| `POST` | `/api/proposals/{id}/offers/{offerId}/accept` | Counterparty | Accept counterparty's pending offer |
| `POST` | `/api/proposals/{id}/offers/{offerId}/counter` | Counterparty | Submit counter-offer with updated price and duration |
| `POST` | `/api/proposals/{id}/offers/{offerId}/withdraw` | Offer Author | Withdraw own pending offer |
| `POST` | `/api/proposals/{id}/accept` | `CLIENT` (Owner) | Accept proposal: creates frozen agreement and snapshot |

---

## 5. Agreements & On-Chain Escrow

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/api/agreements/my` | Authenticated | List agreements where user is client or freelancer |
| `GET` | `/api/agreements/{id}` | Parties / Admin | Get agreement details, snapshot, and escrow metadata |
| `POST` | `/api/agreements/{id}/wallets` | Parties | Link verified wallet to the agreement |
| `POST` | `/api/agreements/{id}/escrow/register` | `CLIENT` (Owner) | Register on-chain factory deployment transaction hash |
| `POST` | `/api/agreements/{id}/escrow/verify-fund` | `CLIENT` (Owner) | Verify funding deposit on-chain; unlocks Milestone 1 |
| `POST` | `/api/agreements/{id}/reconcile` | Parties / Admin | Synchronize contract state with MySQL workflow |
| `GET` | `/api/chain/config` | Authenticated | Get chain ID, factory address, admin address, and rate |
| `GET` | `/api/transactions/mine` | Authenticated | List recorded blockchain transactions for current user |

---

## 6. Milestones, Submissions & Revisions

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/agreements/{id}/milestones/generate` | `CLIENT` (Owner) | Generate scaled milestones from plan (idempotent) |
| `GET` | `/api/agreements/{id}/milestones` | Parties / Admin | List milestone allocations and progress for agreement |
| `GET` | `/api/milestones/{id}` | Parties / Admin | Get single milestone details |
| `POST` | `/api/milestones/{id}/submissions` | `FREELANCER` | Submit work description and deliverables links |
| `GET` | `/api/milestones/{id}/submissions` | Parties / Admin | List submission history for milestone |
| `POST` | `/api/submissions/{id}/approve` | `CLIENT` (Owner) | Verify on-chain release tx and approve milestone payout |
| `POST` | `/api/submissions/{id}/request-revision` | `CLIENT` (Owner) | Request revisions with mandatory feedback |

---

## 7. Deadline Extensions

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/milestones/{id}/extensions` | `FREELANCER` | Request deadline extension with reason |
| `GET` | `/api/milestones/{id}/extensions` | Parties / Admin | List extension requests for milestone |
| `POST` | `/api/extensions/{id}/approve` | `CLIENT` (Owner) | Approve requested deadline extension |
| `POST` | `/api/extensions/{id}/reject` | `CLIENT` (Owner) | Reject deadline extension request |

---

## 8. Mutual Settlements

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/milestones/{id}/settlements` | Parties | Propose agreed split ($f + c \equiv \text{allocation}$) |
| `GET` | `/api/milestones/{id}/settlements` | Parties / Admin | List settlement proposals for milestone |
| `POST` | `/api/settlements/{id}/accept` | Counterparty | Verify on-chain settlement tx and execute payout split |
| `POST` | `/api/settlements/{id}/withdraw` | Proposer | Withdraw own active settlement proposal |

---

## 9. Disputes & Admin Arbitration

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/milestones/{id}/disputes` | Parties | Open dispute on-chain; freezes milestone |
| `GET` | `/api/milestones/{id}/disputes` | Parties / Admin | List dispute records for milestone |
| `GET` | `/api/disputes/{id}` | Parties / Admin | Get dispute details and evidence rebuttal thread |
| `POST` | `/api/disputes/{id}/responses` | Parties | Submit evidence or rebuttal response |
| `GET` | `/api/admin/disputes` | `ADMIN` | List platform disputes filtered by status (paginated) |
| `POST` | `/api/admin/disputes/{id}/decision` | `ADMIN` | Record admin decision (`FREELANCER_FULL`, `CLIENT_FULL`, `SPLIT`) |
| `POST` | `/api/admin/disputes/{id}/resolution-tx` | `ADMIN` | Verify on-chain admin resolution tx and disburse funds |

---

## 10. Reviews & Ratings

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/agreements/{id}/reviews` | Parties | Review partner upon agreement completion (rating 1-5) |
| `GET` | `/api/users/{id}/reviews` | Authenticated | View public reviews and ratings for a user |
