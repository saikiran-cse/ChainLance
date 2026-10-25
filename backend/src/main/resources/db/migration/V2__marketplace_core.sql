-- V2 marketplace core (Stage 4). V1 is never edited. Money: DECIMAL(15,2) = INR (indicative), as in V1.
-- Times: DATETIME(3), stored in UTC by the application.

-- ---------------------------------------------------------------- 1. proposals: one ACTIVE proposal per project
-- V1 had UNIQUE(project_id, freelancer_id), which stops a freelancer from resubmitting after a REJECTED or WITHDRAWN proposal.
-- MySQL has no partial unique index, so a STORED generated column + UNIQUE index is used (same trick as V1):
-- active_key is non-NULL only while the proposal is SUBMITTED, SHORTLISTED or ACCEPTED, so only those rows compete.
-- The FK on project_id needs an index, so a plain one is added BEFORE the old unique index is dropped.
ALTER TABLE proposals ADD KEY ix_proposals_project (project_id);
ALTER TABLE proposals DROP INDEX uq_proposal_project_freelancer;
ALTER TABLE proposals ADD COLUMN active_key VARCHAR(41) GENERATED ALWAYS AS (CASE WHEN status IN ('SUBMITTED','SHORTLISTED','ACCEPTED') THEN CONCAT(project_id, '-', freelancer_id) ELSE NULL END) STORED;
ALTER TABLE proposals ADD UNIQUE KEY uq_proposal_one_active (active_key);

-- ---------------------------------------------------------------- 2. price negotiation history
-- Every price proposal is one row. The freelancer's first proposal is offer number 1.
-- PENDING   = waiting for the OTHER party to accept or counter
-- ACCEPTED  = the other party accepted, so the terms are agreed (at most one per proposal)
-- COUNTERED = the other party answered with a new offer
-- WITHDRAWN = the author took it back, CLOSED = the proposal ended (rejected, withdrawn, project closed)
-- Generated keys make the database itself refuse a second PENDING or a second ACCEPTED offer for one proposal.
CREATE TABLE negotiation_offers (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  proposal_id   BIGINT        NOT NULL,
  offered_by    BIGINT        NOT NULL,
  price_inr     DECIMAL(15,2) NOT NULL,
  duration_days INT           NOT NULL,
  note          VARCHAR(1000) NULL,
  status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  responded_by  BIGINT        NULL,
  responded_at  DATETIME(3)   NULL,
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  pending_key   BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN proposal_id ELSE NULL END) STORED,
  accepted_key  BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACCEPTED' THEN proposal_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uq_offer_one_pending (pending_key),
  UNIQUE KEY uq_offer_one_accepted (accepted_key),
  KEY ix_offers_proposal_created (proposal_id, created_at),
  CONSTRAINT fk_offers_proposal     FOREIGN KEY (proposal_id)  REFERENCES proposals (id),
  CONSTRAINT fk_offers_offered_by   FOREIGN KEY (offered_by)   REFERENCES users (id),
  CONSTRAINT fk_offers_responded_by FOREIGN KEY (responded_by) REFERENCES users (id),
  CONSTRAINT ck_offers_price    CHECK (price_inr > 0),
  CONSTRAINT ck_offers_duration CHECK (duration_days > 0),
  CONSTRAINT ck_offers_status   CHECK (status IN ('PENDING','ACCEPTED','COUNTERED','WITHDRAWN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
