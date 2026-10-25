# ChainLance escrow: contracts, lifecycle and integration boundary (Stage 5)

Scope of this document: the **smart-contract layer** in `contracts/`. The backend and frontend integration is
described as an interface contract in sections 8 and 9. It is **not implemented** and **not verified**.

## 1. Status

| Part | Status |
|---|---|
| Contracts compile, 33 baseline Hardhat tests | Verified by the project owner (before Stage 5) |
| Stage 5 factory change and 16 new hardening tests | Written, **awaiting `npm test` on the owner's machine** |
| Agreement-to-escrow backend integration | Not implemented, unverified (Stage 4 itself is still being tested) |
| Frontend escrow UI | Not implemented |
| Sepolia deployment | Not done, requires explicit owner action |

## 2. Architecture

```
ChainLanceFactory (owner = platform admin, Ownable2Step, renounce disabled)
   createEscrow(freelancer, projectId, allocations[])   msg.sender becomes the client
        |
        v
ChainLanceEscrow  (one per agreement, immutable parties, native ETH only, not upgradeable)
```

* **Currency:** native ETH (Sepolia ETH in testing). Amounts are `uint256` wei. INR is only an off-chain reference
  figure; the backend converts it to wei when the agreement is created (Stage 4) and never sends INR on-chain.
* **Authority split:** the contract is authoritative for funds and payouts. MySQL is authoritative for workflow
  (proposals, negotiation, deadlines, submissions, evidence).
* **Admin:** copied from the factory owner into each escrow at creation; the admin cannot be the client or the
  freelancer of that escrow. Rotating the owner affects only escrows created afterwards.

## 3. Lifecycle

```
createEscrow  ->  (backend verifies the escrow)  ->  fund (exact total, once)
                                                       |
        each milestone, independently, resolved exactly once, by ONE of:
        approveMilestone            client       -> full allocation to the freelancer
        propose + acceptSettlement  both parties -> exact agreed split (a full refund is f = 0)
        openDispute + resolveDispute party opens, admin splits the milestone's own allocation
                                                       |
                         last milestone resolved  ->  EscrowCompleted
```

Milestone states: `Open`, `Released`, `Settled`, `Disputed`, `DisputeResolved`. Work states such as
Pending, In progress, Submitted and Revision requested exist **only in MySQL**.

## 4. Functions

| Function | Caller | Requires | Effect |
|---|---|---|---|
| `Factory.createEscrow(freelancer, projectId, allocations)` | anyone (becomes client) | caller has not used this `projectId` before; valid parties; 1 to 20 non-zero allocations; no overflow | deploys an escrow, records it, emits `EscrowCreated` |
| `fund()` payable | client | not yet funded; `msg.value == totalAmount` exactly | marks funded, emits `Funded` |
| `approveMilestone(i)` | client | funded; milestone `Open` | pays the milestone's allocation to the freelancer |
| `proposeSettlement(i, f, c)` | client or freelancer | funded; `Open`; `f + c == allocation` | stores (replaces) the single proposal |
| `withdrawSettlement(i)` | the proposer | an active proposal | deletes it |
| `acceptSettlement(i, f, c)` | the OTHER party | `Open`; same amounts as the stored proposal | pays `f` and `c`; amounts are re-supplied so a replaced proposal cannot be accepted by mistake |
| `openDispute(i, reasonHash)` | client or freelancer | funded; `Open` | freezes the milestone, clears any proposal |
| `resolveDispute(i, f, c, resolutionHash)` | admin | `Disputed`; `f + c == allocation` | pays `f` and `c`, once |
| views | anyone | | `milestoneCount`, `getMilestone`, `getSettlementProposal`, `remainingEscrow`, `contractBalance`, `isCompleted`, `funded`, `totalAmount`, `totalPaidToFreelancer`, `totalRefundedToClient`, `unresolvedMilestones`, `client`, `freelancer`, `admin`, `projectId` |

There is no function that withdraws arbitrary funds, no unilateral client refund and no admin transfer.

## 5. Events

| Event | Emitted by | When |
|---|---|---|
| `EscrowCreated(projectId indexed, escrow indexed, client indexed, freelancer, admin, totalAmount)` | factory | escrow created |
| `Funded(client indexed, amount)` | escrow | funded |
| `MilestoneReleased(index indexed, freelancer indexed, amount)` | escrow | approved |
| `SettlementProposed(index indexed, proposer indexed, f, c)` / `SettlementWithdrawn(index indexed, proposer indexed)` | escrow | proposal stored / withdrawn |
| `MilestoneSettled(index indexed, f, c)` | escrow | agreed split executed |
| `DisputeOpened(index indexed, openedBy indexed, reasonHash)` | escrow | dispute opened |
| `DisputeResolved(index indexed, admin indexed, f, c, resolutionHash)` | escrow | admin split executed |
| `EscrowCompleted()` | escrow | the last milestone is resolved |

Work **submissions are not on-chain**. They are off-chain workflow in MySQL by design, so there is no submission event.

## 6. How the stated requirements are met

| Requirement | Where |
|---|---|
| Escrow corresponds to the agreement | `projectId = keccak256(abi.encodePacked(uint256 agreementId, bytes32 snapshotHash))`, verified by the backend (section 8). The contract treats it as an opaque id. |
| Parties validated | constructor: no zero address, client != freelancer, admin is neither |
| Total and allocations valid | each allocation > 0, 1 to 20 milestones, `totalAmount` is the checked sum; the backend must equal it to `agreements.total_wei` |
| Full upfront funding | `fund()` accepts exactly `totalAmount`, once, only from the client |
| No duplicate escrow for an agreement | factory rejects a repeated `(client, projectId)`; the backend allows exactly one registered escrow per agreement (section 8) |
| Release pays only its own allocation | every path ends in `_close`, which pays exactly that milestone's `f + c == allocation` |
| No double release | a milestone leaves `Open`/`Disputed` exactly once |
| Reentrancy and failed transfers | `nonReentrant` on every state-changing function; state is final before any ETH is sent; a failed transfer reverts the whole call |
| Late work and extensions | not enforced on-chain. Extensions are an off-chain request and decision. A late payment is a **settlement both parties accept** or a dispute, never an automatic penalty. No penalty percentage exists. |
| Disputes without a backdoor | the admin can only split one `Disputed` milestone's own allocation |

## 7. Security notes and known limitations

**Stage 5 audit finding (fixed, awaiting your test run):** the factory registry was global, so anyone could
front-run a client's `createEscrow` using the same public `projectId` and block that agreement from ever getting an
escrow. The registry is now scoped per caller: `escrowOf(client, projectId)`. No funds were at risk.

Known limitations (documented, not changed):

1. **Verify before funding.** The contract cannot know who the "right" freelancer is. A client could create an escrow
   naming a wrong address and fund it. The UI must not offer **Fund** until the backend has verified the creation
   transaction and the parties (section 8).
2. **Several client wallets.** A user may link up to 5 wallets, and the factory scope is per wallet, so one agreement
   could get escrows from two wallets. The backend must register only one per agreement and ignore the rest.
3. **Deadlines are not on-chain.**
4. **Disputes are resolved only by the admin.** Once a milestone is `Disputed`, the parties cannot settle it between
   themselves. Losing the admin key leaves disputed milestones of existing escrows unresolvable.
5. **Frivolous disputes.** Either party can freeze an `Open` milestone. This costs the admin's time; mitigate off-chain.
6. **Contract wallets that reject ETH.** A payout to such an address reverts and changes nothing. This only blocks
   the rejecting party's own share (a refund-only or pay-only resolution still works).
7. **Force-sent ETH** (selfdestruct) is never counted and cannot be withdrawn.
8. No upgrade path and no pause switch. Escrows are immutable.
9. The INR-to-ETH rate is a fixed demo value (Stage 4). A real price source is a later stage.

## 8. Backend integration boundary (NOT implemented)

Facts about the Stage 4 agreement that escrow depends on:

* `total_wei` is immutable and was rounded **down** from the INR price. The contract's `totalAmount` must equal it exactly.
* **Milestones are not created yet.** Allocations in wei must **sum exactly to `total_wei`** (use whole wei; give any
  rounding remainder to the last milestone). How the project's INR milestone plan maps onto the negotiated price
  is an open design decision for the milestone stage.
* The snapshot stores each user's **oldest linked wallet or none**, and the snapshot is immutable. The escrow needs an
  **explicit wallet choice** written to `agreements.client_wallet` / `freelancer_wallet` (those columns stay updatable).
* `agreements` already has `onchain_project_id`, `factory_address`, `escrow_address` and `chain_id` (updatable).

Required verification before any state change (never trust a status or amount sent by the browser):

| Step | Check against the chain, then update MySQL |
|---|---|
| CREATE_ESCROW | receipt status 1; chain id correct; `to` is the configured factory; one `EscrowCreated` log from the factory whose `projectId` equals the agreement's, `client` equals the agreement's client wallet, `freelancer` equals its freelancer wallet, `admin` is the configured admin, `totalAmount == total_wei`; read back `milestoneCount` and every allocation. Only then store `escrow_address`, `factory_address`, `chain_id`, `onchain_project_id`. |
| FUND | receipt status 1; `to` is the registered escrow; a `Funded(client, amount)` log from it with `amount == total_wei`; `funded() == true`. Only then mark the agreement FUNDED. |
| Later events | verify the matching event from the registered escrow and read `getMilestone(i)` back; `POST /agreements/{id}/reconcile` re-reads the contract and repairs MySQL. |

Also: one `blockchain_transactions` row per `tx_hash` (idempotent), the caller must be a participant of the agreement,
the backend holds no private key, and MySQL and Ethereum are never treated as one atomic transaction.

## 9. Frontend interface contract (NOT implemented)

* ABIs: `contracts/artifacts/contracts/ChainLanceFactory.sol/ChainLanceFactory.json` and
  `.../ChainLanceEscrow.sol/ChainLanceEscrow.json` after `npm run compile`.
* The user signs every transaction in MetaMask. Check the chain id first (Sepolia is `11155111`, local Hardhat `31337`).
* Show **Paid**, **Funded** and similar only after the backend reports the verified chain state. Show pending and
  failed states honestly. Link a hash as `https://sepolia.etherscan.io/tx/<hash>`.
* Show ETH amounts as ETH or wei with the INR figure labelled as an indicative reference.
* Order: create the escrow, wait for backend verification, then enable **Fund**.

## 10. Configuration and commands

`contracts/.env` (never commit; placeholders only):

```
SEPOLIA_RPC_URL=<your Sepolia RPC URL>
DEPLOYER_PRIVATE_KEY=<private key of a THROWAWAY test wallet, 0x-prefixed>
ADMIN_ADDRESS=<address (not key) of the platform admin wallet>
ETHERSCAN_API_KEY=<optional>
```

From `contracts/` in Windows PowerShell:

```powershell
npm run compile
npm test
npx hardhat node                                  # terminal 1
npm run deploy:local                              # terminal 2: writes deployments/localhost.json
npm run deploy:sepolia                            # only when you choose to; needs the .env values
```

## 11. Tests

* `test/ChainLanceEscrow.test.js`: 33 baseline tests (three `escrowOf` lookups updated for the new two-argument getter).
* `test/ChainLanceEscrow.hardening.test.js`: 16 new tests: front-run protection, project id encoding vector,
  overflow, failed transfers (rejecting freelancer, rejecting client contract, forced ETH), a full state-machine matrix,
  `EscrowCompleted` emission and a seeded randomized accounting-invariant sweep.
* Mocks (test only): `ReentrantFreelancer`, `EthRejecter`, `RejectingClient`, `ForceSend`.
