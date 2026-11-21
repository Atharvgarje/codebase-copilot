DROP INDEX IF EXISTS ix_code_chunks_embedding_hnsw;

-- Existing vectors came from a different model and cannot be reused at 768 dimensions.
ALTER TABLE code_chunks
    ALTER COLUMN embedding TYPE VECTOR(768)
    USING NULL::VECTOR(768);

CREATE INDEX ix_code_chunks_embedding_hnsw
    ON code_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE embedding IS NOT NULL;
