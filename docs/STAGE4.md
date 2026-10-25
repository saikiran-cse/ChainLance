# Stage 4: Marketplace core (projects, proposals, negotiation, agreements)

This stage lets a **client** post a project, **freelancers** bid on it, both sides **negotiate the price**, and the
backend then stores a **frozen agreement snapshot**. There is no escrow, payment or milestone logic yet.

## The story in 7 steps

1. The client creates a project (it starts as **DRAFT**, which only the client can see) and **publishes** it (**OPEN**).
2. A freelancer browses OPEN projects and submits a proposal with a price. That price becomes **offer #1**.
3. The client can **accept** the offer or send a **counteroffer**. The freelancer can then accept or counter back, and so on.
   Only the *other* person can answer an offer. Every offer is saved, so there is a full history.
4. When one side accepts the other side's offer, the **terms are agreed**.
5. The client calls **accept proposal**. In ONE database transaction the backend:
   marks the proposal ACCEPTED, creates the agreement snapshot, sets the project to IN_PROGRESS and rejects the
   project's other open proposals. If anything fails, none of it is saved.
6. The agreement is **read-only** from then on. There is no edit endpoint.
7. Later stages (milestones, escrow) will build on this agreement.

## Endpoints

| Method and path | Who | What it does |
|---|---|---|
| `POST /api/projects` | client | create a DRAFT project |
| `GET /api/projects?category=&page=&size=` | anyone logged in | list OPEN projects (newest first, size max 50) |
| `GET /api/projects/mine` | client | my projects in every status |
| `GET /api/projects/{id}` | see below | project details |
| `PUT /api/projects/{id}` | owner | edit while DRAFT or OPEN (budget locks once proposals exist) |
| `POST /api/projects/{id}/publish` / `close` / `cancel` | owner | DRAFT to OPEN / OPEN to CLOSED / DRAFT or OPEN to CANCELLED |
| `POST /api/projects/{projectId}/proposals` | freelancer | submit a proposal (also creates offer #1) |
| `GET /api/projects/{projectId}/proposals` | owner | proposals on my project |
| `GET /api/proposals/my` | freelancer | my proposals |
| `GET /api/proposals/{id}` | the two parties | proposal plus negotiation history |
| `POST /api/proposals/{id}/shortlist` / `reject` | owner | review a proposal |
| `POST /api/proposals/{id}/withdraw` | the freelancer | withdraw my proposal |
| `GET /api/proposals/{id}/offers` | the two parties | negotiation history |
| `POST /api/proposals/{id}/offers` | the two parties | new offer (only when nothing is pending) |
| `POST /api/proposals/{id}/offers/{offerId}/accept` | the other party | accept the pending offer |
| `POST /api/proposals/{id}/offers/{offerId}/counter` | the other party | answer with a new price |
| `POST /api/proposals/{id}/offers/{offerId}/withdraw` | the offer's author | take back my own pending offer |
| `POST /api/proposals/{id}/accept` | owner | accept the proposal AND create the agreement |
| `GET /api/agreements/my`, `GET /api/agreements/{id}` | the two parties | read agreements |

A project that is not OPEN is visible only to its client, an admin, or a freelancer who has a proposal on it.
Everything else answers 404 so nobody can discover which ids exist.

## Money and the demo exchange rate

* Prices are negotiated in **INR** (`BigDecimal`, exactly 2 decimals, stored as `DECIMAL(15,2)`), as the existing schema already does.
* Offers cannot be higher than the project budget.
* When the agreement is created, the INR price is converted to **wei** with a fixed demo rate
  (`chainlance.pricing.inr-per-eth`, default `250000`, override with the `INR_PER_ETH` environment variable).
  The rate and the result are written into the snapshot, so they can never change later.
  Example: 55,000 INR at 250,000 INR/ETH = 0.22 ETH = `220000000000000000` wei.
* **This rate is a demo value.** A later stage should replace it with a real price source.

## What "immutable snapshot" means here

* The snapshot (`agreements.snapshot_json`) is a copy of the project terms, both parties, the proposal, the agreed
  price and duration, the pricing details, the wallets (if verified) and the full negotiation history.
* The hash (`snapshot_hash`) is keccak256 of the snapshot's canonical JSON (sorted keys, no spaces).
  Every read recomputes it and returns `snapshotIntegrityOk`.
* Protection is at **application level**: no update endpoint, no service method that edits it, and the JPA columns are
  `updatable = false`. Someone with direct database access could still change a row, but the hash would then no longer
  match. Real tamper-proofing (anchoring the hash on the blockchain) belongs to a later stage.
* I did not use MySQL triggers: on a normal local MySQL 8 they often need extra privileges and would break Flyway.

## Database changes (Flyway `V2__marketplace_core.sql`, V1 is untouched)

* New table `negotiation_offers` with generated unique keys so MySQL itself allows only one PENDING and one ACCEPTED offer per proposal.
* `proposals`: the old `UNIQUE(project_id, freelancer_id)` is replaced by a unique key over **active** proposals only,
  so a freelancer can resubmit after a rejection or withdrawal but never has two live proposals.

## Changes to existing files

* `Agreement.java`: four columns are now `updatable = false` (see above).
* `ProposalRepository.java`: `findByProjectIdAndFreelancerId` (returned one result) is replaced by a list version,
  because a freelancer can now have several proposals per project over time. Nothing used the old method.
* `application.yml` and `.env.example`: the `INR_PER_ETH` setting.

## Known limits

* Milestones are not created yet. The snapshot includes the project's milestone plan rows if any exist.
* No true multi-thread concurrency test. Protection is the proposal row lock plus the unique keys, and the duplicate
  case is tested one request after another plus a direct duplicate insert.
* The wallet addresses in a snapshot are the user's oldest verified wallet (or absent). Escrow will need to pick one explicitly.
