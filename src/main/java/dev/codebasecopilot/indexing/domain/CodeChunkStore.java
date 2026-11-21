package dev.codebasecopilot.indexing.domain;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CodeChunkStore {

    private final JdbcTemplate jdbc;

    public CodeChunkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(List<CodeChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Each code chunk must have a matching embedding slot");
        }
        AtomicInteger embeddingIndex = new AtomicInteger();
        jdbc.batchUpdate("""
                INSERT INTO code_chunks
                    (id, project_id, revision, file_path, start_line, end_line, content, content_sha256, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (project_id, revision, file_path, start_line, end_line)
                DO UPDATE SET content = EXCLUDED.content,
                              content_sha256 = EXCLUDED.content_sha256,
                              embedding = COALESCE(code_chunks.embedding, EXCLUDED.embedding)
                """, chunks, 100, (statement, chunk) -> {
            int index = embeddingIndex.getAndIncrement();
            statement.setObject(1, chunk.id());
            statement.setObject(2, chunk.projectId());
            statement.setString(3, chunk.revision());
            statement.setString(4, chunk.filePath());
            statement.setInt(5, chunk.startLine());
            statement.setInt(6, chunk.endLine());
            statement.setString(7, chunk.content());
            statement.setString(8, chunk.contentSha256());
            float[] embedding = embeddings.get(index);
            statement.setString(9, embedding == null ? null : toVectorLiteral(embedding));
        });
    }

    public void setIndexedRevision(UUID projectId, String revision) {
        jdbc.update("UPDATE projects SET indexed_revision = ?, updated_at = now() WHERE id = ?", revision, projectId);
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) literal.append(',');
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }
}
