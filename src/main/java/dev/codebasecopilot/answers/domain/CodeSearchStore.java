package dev.codebasecopilot.answers.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CodeSearchStore {

    private final JdbcTemplate jdbc;

    public CodeSearchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CodeMatch> search(UUID projectId, String revision, String question, float[] queryVector, int limit) {
        if (queryVector == null) {
            return jdbc.query("""
                    SELECT file_path, start_line, end_line, content,
                           ts_rank_cd(search_vector, plainto_tsquery('english', ?)) AS score
                    FROM code_chunks
                    WHERE project_id = ? AND revision = ?
                      AND search_vector @@ plainto_tsquery('english', ?)
                    ORDER BY score DESC, file_path, start_line
                    LIMIT ?
                    """, (rs, row) -> new CodeMatch(rs.getString("file_path"), rs.getInt("start_line"),
                            rs.getInt("end_line"), rs.getString("content"), rs.getDouble("score")),
                    question, projectId, revision, question, limit);
        }

        String vectorLiteral = toVectorLiteral(queryVector);
        return jdbc.query("""
                WITH lexical AS (
                    SELECT file_path, start_line, end_line, content,
                           ts_rank_cd(search_vector, plainto_tsquery('english', ?)) AS lexical_score,
                           0::real AS semantic_score
                    FROM code_chunks
                    WHERE project_id = ? AND revision = ?
                      AND search_vector @@ plainto_tsquery('english', ?)
                    ORDER BY lexical_score DESC
                    LIMIT 30
                ), semantic AS (
                    SELECT file_path, start_line, end_line, content, 0::real AS lexical_score,
                           1 - (embedding <=> CAST(? AS vector)) AS semantic_score
                    FROM code_chunks
                    WHERE project_id = ? AND revision = ? AND embedding IS NOT NULL
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT 30
                ), combined AS (
                    SELECT file_path, start_line, end_line, content,
                           MAX(lexical_score) AS lexical_score, MAX(semantic_score) AS semantic_score
                    FROM (SELECT * FROM lexical UNION ALL SELECT * FROM semantic) matches
                    GROUP BY file_path, start_line, end_line, content
                )
                SELECT file_path, start_line, end_line, content,
                       lexical_score + (semantic_score * 0.5) AS score
                FROM combined
                ORDER BY score DESC, file_path, start_line
                LIMIT ?
                """, (rs, row) -> new CodeMatch(rs.getString("file_path"), rs.getInt("start_line"),
                        rs.getInt("end_line"), rs.getString("content"), rs.getDouble("score")),
                question, projectId, revision, question,
                vectorLiteral, projectId, revision, vectorLiteral, limit);
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
