ALTER TABLE projects ADD COLUMN indexed_revision VARCHAR(128);

ALTER TABLE code_chunks
    ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (to_tsvector('english', coalesce(file_path, '') || ' ' || content)) STORED;

CREATE INDEX ix_code_chunks_search ON code_chunks USING GIN(search_vector);
