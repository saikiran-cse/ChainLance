-- V3 milestone indices. V1 and V2 are never edited.
-- Allows 0-indexed milestones and plans (position >= 0) to directly match
-- the smart contract's 0-indexed milestone arrays (index = 0, 1, ..., N-1).

ALTER TABLE milestones DROP CHECK ck_milestones_position;
ALTER TABLE milestones ADD CONSTRAINT ck_milestones_position CHECK (position >= 0);

ALTER TABLE project_milestone_plans DROP CHECK ck_plan_position;
ALTER TABLE project_milestone_plans ADD CONSTRAINT ck_plan_position CHECK (position >= 0);
