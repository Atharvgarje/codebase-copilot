package dev.codebasecopilot.indexing.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IndexingJobStore {

    private static final RowMapper<IndexingJob> ROW_MAPPER = IndexingJobStore::mapRow;
    private final JdbcTemplate jdbc;

    public IndexingJobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public IndexingJob createOrGetActive(UUID projectId) {
        for (int retry = 0; retry < 3; retry++) {
            List<IndexingJob> inserted = jdbc.query("""
                    INSERT INTO indexing_jobs (id, project_id, status)
                    VALUES (?, ?, 'QUEUED')
                    ON CONFLICT (project_id) WHERE status IN ('QUEUED', 'RUNNING') DO NOTHING
                    RETURNING id, project_id, status, attempt, requested_at, started_at, completed_at, error_code
                    """, ROW_MAPPER, UUID.randomUUID(), projectId);

            if (!inserted.isEmpty()) {
                return inserted.getFirst();
            }

            List<IndexingJob> active = jdbc.query("""
                    SELECT id, project_id, status, attempt, requested_at, started_at, completed_at, error_code
                    FROM indexing_jobs
                    WHERE project_id = ? AND status IN ('QUEUED', 'RUNNING')
                    ORDER BY requested_at DESC
                    LIMIT 1
                    """, ROW_MAPPER, projectId);
            if (!active.isEmpty()) {
                return active.getFirst();
            }
        }
        throw new IllegalStateException("Could not create or locate an active indexing job");
    }

    public Optional<IndexingJob> findById(UUID id) {
        return jdbc.query("""
                SELECT id, project_id, status, attempt, requested_at, started_at, completed_at, error_code
                FROM indexing_jobs WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    public List<IndexingJob> findByProjectId(UUID projectId) {
        return jdbc.query("""
                SELECT id, project_id, status, attempt, requested_at, started_at, completed_at, error_code
                FROM indexing_jobs WHERE project_id = ? ORDER BY requested_at DESC
                """, ROW_MAPPER, projectId);
    }

    /** Atomically leases one eligible job; concurrent workers skip rows already locked by a peer. */
    public Optional<IndexingJob> claimNext(String workerId) {
        jdbc.update("""
                UPDATE indexing_jobs
                SET status = 'FAILED', completed_at = now(), error_code = 'MAX_ATTEMPTS_EXCEEDED',
                    locked_by = NULL, locked_until = NULL
                WHERE status = 'RUNNING' AND locked_until <= now() AND attempt >= 5
                """);
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id
                    FROM indexing_jobs
                    WHERE (status = 'QUEUED' AND next_attempt_at <= now())
                       OR (status = 'RUNNING' AND locked_until <= now())
                    ORDER BY requested_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE indexing_jobs AS job
                SET status = 'RUNNING',
                    attempt = job.attempt + 1,
                    started_at = COALESCE(job.started_at, now()),
                    locked_by = ?,
                    locked_until = now() + interval '15 minutes'
                FROM candidate
                WHERE job.id = candidate.id
                RETURNING job.id, job.project_id, job.status, job.attempt,
                          job.requested_at, job.started_at, job.completed_at, job.error_code
                """, ROW_MAPPER, workerId).stream().findFirst();
    }

    @Transactional
    public boolean complete(UUID jobId, UUID projectId, String workerId, String revision) {
        boolean completed = jdbc.update("""
                UPDATE indexing_jobs
                SET status = 'SUCCEEDED', completed_at = now(), locked_by = NULL, locked_until = NULL, error_code = NULL
                WHERE id = ? AND status = 'RUNNING' AND locked_by = ?
                """, jobId, workerId) == 1;
        if (!completed) {
            return false;
        }
        jdbc.update("UPDATE projects SET indexed_revision = ?, updated_at = now() WHERE id = ?", revision, projectId);
        return true;
    }

    public boolean renewLease(UUID jobId, String workerId) {
        return jdbc.update("""
                UPDATE indexing_jobs SET locked_until = now() + interval '15 minutes'
                WHERE id = ? AND status = 'RUNNING' AND locked_by = ?
                """, jobId, workerId) == 1;
    }

    public void markFailed(UUID jobId, String workerId, String errorCode, boolean retryable) {
        jdbc.update("""
                UPDATE indexing_jobs
                SET status = CASE WHEN NOT ? OR attempt >= 5 THEN 'FAILED' ELSE 'QUEUED' END,
                    next_attempt_at = now() + (LEAST(300, 15 * power(2, GREATEST(attempt - 1, 0)))::text || ' seconds')::interval,
                    completed_at = CASE WHEN NOT ? OR attempt >= 5 THEN now() ELSE NULL END,
                    error_code = ?, locked_by = NULL, locked_until = NULL
                WHERE id = ? AND status = 'RUNNING' AND locked_by = ?
                """, retryable, retryable, errorCode, jobId, workerId);
    }

    private static IndexingJob mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new IndexingJob(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                IndexingJobStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt"),
                rs.getTimestamp("requested_at").toInstant(),
                rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(),
                rs.getString("error_code"));
    }
}
