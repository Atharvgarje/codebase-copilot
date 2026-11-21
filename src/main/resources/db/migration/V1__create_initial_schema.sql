CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    repository_url VARCHAR(2048) NOT NULL,
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_repository_url UNIQUE (repository_url)
);

CREATE TABLE indexing_jobs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 0,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    CONSTRAINT ck_indexing_jobs_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_indexing_jobs_project_requested ON indexing_jobs(project_id, requested_at DESC);
CREATE INDEX ix_indexing_jobs_claim ON indexing_jobs(status, requested_at) WHERE status = 'QUEUED';

CREATE TABLE code_chunks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    revision VARCHAR(128) NOT NULL,
    file_path VARCHAR(2048) NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    embedding VECTOR(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_code_chunks_revision_file_range UNIQUE (project_id, revision, file_path, start_line, end_line)
);

CREATE INDEX ix_code_chunks_project_revision ON code_chunks(project_id, revision);
