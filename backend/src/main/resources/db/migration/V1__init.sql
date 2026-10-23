-- ChainLance V1 schema (MySQL 8.0.16+, InnoDB, utf8mb4)
-- Money: DECIMAL(38,0) = wei (ETH). DECIMAL(15,2) = INR (indicative only). Never floats.
-- Times: DATETIME(3), stored in UTC by the application.
-- Milestone `position` is 1-based in MySQL; the contract index is position - 1.
-- "At most one active X per milestone" rules use a STORED generated column + UNIQUE index,
-- because MySQL has no partial unique indexes.

-- ---------------------------------------------------------------- users
CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,
  bio           TEXT         NULL,
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  CONSTRAINT ck_users_role CHECK (role IN ('CLIENT','FREELANCER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Addresses are stored lowercase, 0x + 40 hex chars. One address belongs to exactly one user.
CREATE TABLE wallet_addresses (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  address     CHAR(42)    NOT NULL,
  verified_at DATETIME(3) NOT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_wallet_address (address),
  KEY ix_wallet_user (user_id),
  CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Single-use nonce for the sign-in-with-wallet proof. The server stores the exact message it asked the user to sign.
CREATE TABLE wallet_challenges (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  address    CHAR(42)     NOT NULL,
  nonce      VARCHAR(64)  NOT NULL,
  message    VARCHAR(500) NOT NULL,
  expires_at DATETIME(3)  NOT NULL,
  used_at    DATETIME(3)  NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_wallet_challenge_nonce (nonce),
  KEY ix_wallet_challenge_user (user_id),
  CONSTRAINT fk_wallet_challenge_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- projects
CREATE TABLE projects (
  id          BIGINT         NOT NULL AUTO_INCREMENT,
  client_id   BIGINT         NOT NULL,
  title       VARCHAR(200)   NOT NULL,
  description TEXT           NOT NULL,
  category    VARCHAR(80)    NOT NULL,
  skills      VARCHAR(500)   NULL,
  budget_inr  DECIMAL(15,2)  NOT NULL,
  deadline    DATETIME(3)    NULL,
  status      VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
  created_at  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_projects_client (client_id),
  KEY ix_projects_status_created (status, created_at),
  KEY ix_projects_category (category),
  CONSTRAINT fk_projects_client FOREIGN KEY (client_id) REFERENCES users (id),
  CONSTRAINT ck_projects_budget CHECK (budget_inr > 0),
  CONSTRAINT ck_projects_status CHECK (status IN ('DRAFT','OPEN','IN_PROGRESS','COMPLETED','CLOSED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_milestone_plans (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  project_id     BIGINT        NOT NULL,
  position       INT           NOT NULL,
  description    VARCHAR(500)  NOT NULL,
  allocation_inr DECIMAL(15,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_plan_position (project_id, position),
  CONSTRAINT fk_plan_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT ck_plan_position CHECK (position >= 1),
  CONSTRAINT ck_plan_allocation CHECK (allocation_inr > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- proposals and negotiation
CREATE TABLE proposals (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  project_id      BIGINT        NOT NULL,
  freelancer_id   BIGINT        NOT NULL,
  message         TEXT          NOT NULL,
  price_inr       DECIMAL(15,2) NOT NULL,
  duration_days   INT           NOT NULL,
  portfolio_links VARCHAR(1000) NULL,
  status          VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED',
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_proposal_project_freelancer (project_id, freelancer_id),
  KEY ix_proposals_freelancer (freelancer_id),
  CONSTRAINT fk_proposals_project    FOREIGN KEY (project_id)    REFERENCES projects (id),
  CONSTRAINT fk_proposals_freelancer FOREIGN KEY (freelancer_id) REFERENCES users (id),
  CONSTRAINT ck_proposals_price    CHECK (price_inr > 0),
  CONSTRAINT ck_proposals_duration CHECK (duration_days > 0),
  CONSTRAINT ck_proposals_status   CHECK (status IN ('SUBMITTED','SHORTLISTED','ACCEPTED','REJECTED','WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE negotiation_messages (
  id                  BIGINT      NOT NULL AUTO_INCREMENT,
  proposal_id         BIGINT      NOT NULL,
  sender_id           BIGINT      NOT NULL,
  message             TEXT        NOT NULL,
  proposed_terms_json JSON        NULL,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_negotiation_proposal_created (proposal_id, created_at),
  CONSTRAINT fk_negotiation_proposal FOREIGN KEY (proposal_id) REFERENCES proposals (id),
  CONSTRAINT fk_negotiation_sender   FOREIGN KEY (sender_id)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- agreements and milestones
-- Schema additions vs ARCHITECTURE.md section 3 (all required by the workflow):
--   client_wallet / freelancer_wallet : the exact addresses frozen into the snapshot; the backend compares them with
--                                       the on-chain client/freelancer when it verifies the escrow.
--   onchain_project_id                : keccak256(agreement id || snapshot hash), the bytes32 passed to createEscrow.
--   factory_address                   : which factory created the escrow (needed if the factory is redeployed).
--   version                           : optimistic locking so two verifications cannot overwrite each other.
CREATE TABLE agreements (
  id                      BIGINT         NOT NULL AUTO_INCREMENT,
  proposal_id             BIGINT         NOT NULL,
  total_wei               DECIMAL(38,0)  NOT NULL,
  status                  VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
  client_finalized_at     DATETIME(3)    NULL,
  freelancer_finalized_at DATETIME(3)    NULL,
  snapshot_json           JSON           NULL,
  snapshot_hash           CHAR(66)       NULL,
  client_wallet           CHAR(42)       NULL,
  freelancer_wallet       CHAR(42)       NULL,
  onchain_project_id      CHAR(66)       NULL,
  factory_address         CHAR(42)       NULL,
  escrow_address          CHAR(42)       NULL,
  chain_id                BIGINT         NULL,
  version                 BIGINT         NOT NULL DEFAULT 0,
  created_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_agreement_proposal (proposal_id),
  UNIQUE KEY uq_agreement_escrow (escrow_address),
  UNIQUE KEY uq_agreement_onchain_project (onchain_project_id),
  CONSTRAINT fk_agreements_proposal FOREIGN KEY (proposal_id) REFERENCES proposals (id),
  CONSTRAINT ck_agreements_total  CHECK (total_wei >= 0),
  CONSTRAINT ck_agreements_status CHECK (status IN ('DRAFT','FINALIZED','FUNDED','COMPLETED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- work_status exists only in MySQL. chain_status mirrors the contract and is changed only after a verified transaction.
-- freelancer_paid_wei / client_refunded_wei are copied from verified contract events (never from the browser).
CREATE TABLE milestones (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  agreement_id        BIGINT        NOT NULL,
  position            INT           NOT NULL,
  description         VARCHAR(500)  NOT NULL,
  acceptance_criteria TEXT          NULL,
  amount_wei          DECIMAL(38,0) NOT NULL,
  original_deadline   DATETIME(3)   NULL,
  effective_deadline  DATETIME(3)   NULL,
  work_status         VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
  chain_status        VARCHAR(30)   NOT NULL DEFAULT 'OPEN',
  freelancer_paid_wei DECIMAL(38,0) NOT NULL DEFAULT 0,
  client_refunded_wei DECIMAL(38,0) NOT NULL DEFAULT 0,
  version             BIGINT        NOT NULL DEFAULT 0,
  created_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_milestone_position (agreement_id, position),
  CONSTRAINT fk_milestones_agreement FOREIGN KEY (agreement_id) REFERENCES agreements (id) ON DELETE CASCADE,
  CONSTRAINT ck_milestones_position CHECK (position >= 1),
  CONSTRAINT ck_milestones_amount   CHECK (amount_wei > 0),
  CONSTRAINT ck_milestones_paid     CHECK (freelancer_paid_wei + client_refunded_wei <= amount_wei),
  CONSTRAINT ck_milestones_work     CHECK (work_status IN ('PENDING','IN_PROGRESS','SUBMITTED','REVISION_REQUESTED','APPROVED','CLOSED')),
  CONSTRAINT ck_milestones_chain    CHECK (chain_status IN ('OPEN','RELEASED','SETTLED','DISPUTED','DISPUTE_RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE submissions (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  milestone_id    BIGINT        NOT NULL,
  description     TEXT          NOT NULL,
  links           VARCHAR(2000) NULL,
  submitted_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  is_late         BOOLEAN       NOT NULL DEFAULT FALSE,
  review_status   VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
  revision_reason TEXT          NULL,
  reviewed_at     DATETIME(3)   NULL,
  PRIMARY KEY (id),
  KEY ix_submissions_milestone (milestone_id, submitted_at),
  CONSTRAINT fk_submissions_milestone FOREIGN KEY (milestone_id) REFERENCES milestones (id) ON DELETE CASCADE,
  CONSTRAINT ck_submissions_review CHECK (review_status IN ('PENDING','APPROVED','REVISION_REQUESTED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- blockchain transactions
-- Types extend the architecture list because the contract needs an on-chain propose and an on-chain accept:
--   CREATE_ESCROW, FUND, APPROVE, SETTLE_PROPOSE, SETTLE_WITHDRAW, SETTLE (accept), DISPUTE (open), RESOLVE.
-- UNIQUE(tx_hash) makes verification idempotent: the same hash can never be applied twice.
CREATE TABLE blockchain_transactions (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  tx_hash       CHAR(66)      NOT NULL,
  type          VARCHAR(20)   NOT NULL,
  status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  agreement_id  BIGINT        NULL,
  milestone_id  BIGINT        NULL,
  submitted_by  BIGINT        NULL,
  from_address  CHAR(42)      NULL,
  to_address    CHAR(42)      NULL,
  chain_id      BIGINT        NOT NULL,
  block_number  BIGINT        NULL,
  value_wei     DECIMAL(38,0) NULL,
  event_json    JSON          NULL,
  verified_at   DATETIME(3)   NULL,
  error         VARCHAR(1000) NULL,
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_tx_hash (tx_hash),
  KEY ix_tx_agreement (agreement_id),
  KEY ix_tx_milestone (milestone_id),
  KEY ix_tx_status (status),
  KEY ix_tx_submitted_by (submitted_by),
  CONSTRAINT fk_tx_agreement  FOREIGN KEY (agreement_id) REFERENCES agreements (id),
  CONSTRAINT fk_tx_milestone  FOREIGN KEY (milestone_id) REFERENCES milestones (id),
  CONSTRAINT fk_tx_user       FOREIGN KEY (submitted_by) REFERENCES users (id),
  CONSTRAINT ck_tx_type   CHECK (type IN ('CREATE_ESCROW','FUND','APPROVE','SETTLE_PROPOSE','SETTLE_WITHDRAW','SETTLE','DISPUTE','RESOLVE')),
  CONSTRAINT ck_tx_status CHECK (status IN ('PENDING','CONFIRMED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- deadline extensions
CREATE TABLE deadline_extension_requests (
  id                 BIGINT      NOT NULL AUTO_INCREMENT,
  milestone_id       BIGINT      NOT NULL,
  requested_by       BIGINT      NOT NULL,
  current_deadline   DATETIME(3) NOT NULL,
  requested_deadline DATETIME(3) NOT NULL,
  reason             TEXT        NOT NULL,
  status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  decided_by         BIGINT      NULL,
  decision_note      TEXT        NULL,
  requested_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  decided_at         DATETIME(3) NULL,
  -- non-NULL only while PENDING, so at most one pending request per milestone
  pending_key        BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN milestone_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uq_extension_one_pending (pending_key),
  KEY ix_extension_milestone (milestone_id),
  CONSTRAINT fk_extension_milestone  FOREIGN KEY (milestone_id) REFERENCES milestones (id),
  CONSTRAINT fk_extension_requester  FOREIGN KEY (requested_by) REFERENCES users (id),
  CONSTRAINT fk_extension_decider    FOREIGN KEY (decided_by)   REFERENCES users (id),
  CONSTRAINT ck_extension_dates  CHECK (requested_deadline > current_deadline),
  CONSTRAINT ck_extension_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- settlements
-- Lifecycle: AWAITING_PROPOSE_TX (saved, on-chain proposeSettlement not yet verified)
--            -> ACTIVE (SettlementProposed event verified)
--            -> EXECUTED (MilestoneSettled event verified) | WITHDRAWN | SUPERSEDED | CANCELLED
CREATE TABLE settlement_proposals (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  milestone_id     BIGINT        NOT NULL,
  proposer_id      BIGINT        NOT NULL,
  freelancer_wei   DECIMAL(38,0) NOT NULL,
  client_wei       DECIMAL(38,0) NOT NULL,
  reason           TEXT          NULL,
  status           VARCHAR(30)   NOT NULL DEFAULT 'AWAITING_PROPOSE_TX',
  propose_tx_id    BIGINT        NULL,
  executed_tx_id   BIGINT        NULL,
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  decided_at       DATETIME(3)   NULL,
  live_key         BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('AWAITING_PROPOSE_TX','ACTIVE') THEN milestone_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uq_settlement_one_live (live_key),
  KEY ix_settlement_milestone (milestone_id),
  CONSTRAINT fk_settlement_milestone FOREIGN KEY (milestone_id)   REFERENCES milestones (id),
  CONSTRAINT fk_settlement_proposer  FOREIGN KEY (proposer_id)    REFERENCES users (id),
  CONSTRAINT fk_settlement_prop_tx   FOREIGN KEY (propose_tx_id)  REFERENCES blockchain_transactions (id),
  CONSTRAINT fk_settlement_exec_tx   FOREIGN KEY (executed_tx_id) REFERENCES blockchain_transactions (id),
  CONSTRAINT ck_settlement_amounts CHECK (freelancer_wei >= 0 AND client_wei >= 0),
  CONSTRAINT ck_settlement_status  CHECK (status IN ('AWAITING_PROPOSE_TX','ACTIVE','EXECUTED','WITHDRAWN','SUPERSEDED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- disputes
-- Lifecycle: AWAITING_OPEN_TX (saved, on-chain openDispute not yet verified)
--            -> OPEN (DisputeOpened verified; milestone frozen on-chain)
--            -> DECIDED (admin saved a decision in MySQL; not yet executed on-chain)
--            -> RESOLVED (DisputeResolved event verified) | CANCELLED (open tx failed / abandoned before OPEN)
-- reason_hash / resolution_hash are the bytes32 values passed to the contract (keccak256 of the stored text).
CREATE TABLE disputes (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  milestone_id        BIGINT        NOT NULL,
  opened_by           BIGINT        NOT NULL,
  reason              TEXT          NOT NULL,
  reason_hash         CHAR(66)      NULL,
  status              VARCHAR(30)   NOT NULL DEFAULT 'AWAITING_OPEN_TX',
  admin_decision      VARCHAR(30)   NULL,
  admin_freelancer_wei DECIMAL(38,0) NULL,
  admin_client_wei    DECIMAL(38,0) NULL,
  admin_explanation   TEXT          NULL,
  resolution_hash     CHAR(66)      NULL,
  decided_by          BIGINT        NULL,
  decided_at          DATETIME(3)   NULL,
  open_tx_id          BIGINT        NULL,
  resolved_tx_id      BIGINT        NULL,
  created_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  live_key            BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('AWAITING_OPEN_TX','OPEN','DECIDED') THEN milestone_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uq_dispute_one_live (live_key),
  KEY ix_disputes_milestone (milestone_id),
  KEY ix_disputes_status (status),
  CONSTRAINT fk_disputes_milestone FOREIGN KEY (milestone_id)   REFERENCES milestones (id),
  CONSTRAINT fk_disputes_opener    FOREIGN KEY (opened_by)      REFERENCES users (id),
  CONSTRAINT fk_disputes_decider   FOREIGN KEY (decided_by)     REFERENCES users (id),
  CONSTRAINT fk_disputes_open_tx   FOREIGN KEY (open_tx_id)     REFERENCES blockchain_transactions (id),
  CONSTRAINT fk_disputes_res_tx    FOREIGN KEY (resolved_tx_id) REFERENCES blockchain_transactions (id),
  CONSTRAINT ck_disputes_status   CHECK (status IN ('AWAITING_OPEN_TX','OPEN','DECIDED','RESOLVED','CANCELLED')),
  CONSTRAINT ck_disputes_decision CHECK (admin_decision IS NULL OR admin_decision IN ('FREELANCER_FULL','CLIENT_FULL','SPLIT')),
  CONSTRAINT ck_disputes_amounts  CHECK (admin_freelancer_wei IS NULL OR (admin_freelancer_wei >= 0 AND admin_client_wei >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dispute_responses (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  dispute_id     BIGINT        NOT NULL,
  user_id        BIGINT        NOT NULL,
  response       TEXT          NOT NULL,
  evidence_links VARCHAR(2000) NULL,
  created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_dispute_responses_dispute (dispute_id, created_at),
  CONSTRAINT fk_dispute_responses_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE,
  CONSTRAINT fk_dispute_responses_user    FOREIGN KEY (user_id)    REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- reviews
CREATE TABLE reviews (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  agreement_id BIGINT      NOT NULL,
  reviewer_id  BIGINT      NOT NULL,
  reviewee_id  BIGINT      NOT NULL,
  rating       TINYINT     NOT NULL,
  comment      TEXT        NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_review_once (agreement_id, reviewer_id),
  KEY ix_reviews_reviewee (reviewee_id),
  CONSTRAINT fk_reviews_agreement FOREIGN KEY (agreement_id) REFERENCES agreements (id),
  CONSTRAINT fk_reviews_reviewer  FOREIGN KEY (reviewer_id)  REFERENCES users (id),
  CONSTRAINT fk_reviews_reviewee  FOREIGN KEY (reviewee_id)  REFERENCES users (id),
  CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT ck_reviews_distinct CHECK (reviewer_id <> reviewee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
