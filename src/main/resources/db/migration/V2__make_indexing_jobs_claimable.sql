ALTER TABLE indexing_jobs
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN locked_by VARCHAR(120);

CREATE UNIQUE INDEX uq_indexing_jobs_one_active_per_project
    ON indexing_jobs(project_id)
    WHERE status IN ('QUEUED', 'RUNNING');

DROP INDEX ix_indexing_jobs_claim;
CREATE INDEX ix_indexing_jobs_claim
    ON indexing_jobs(status, next_attempt_at, requested_at)
    WHERE status IN ('QUEUED', 'RUNNING');
