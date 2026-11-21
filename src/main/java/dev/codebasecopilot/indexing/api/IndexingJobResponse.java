package dev.codebasecopilot.indexing.api;

import dev.codebasecopilot.indexing.domain.IndexingJob;
import dev.codebasecopilot.indexing.domain.IndexingJobStatus;
import java.time.Instant;
import java.util.UUID;

public record IndexingJobResponse(
        UUID id,
        UUID projectId,
        IndexingJobStatus status,
        int attempt,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String errorCode) {

    public static IndexingJobResponse from(IndexingJob job) {
        return new IndexingJobResponse(job.id(), job.projectId(), job.status(), job.attempt(),
                job.requestedAt(), job.startedAt(), job.completedAt(), job.errorCode());
    }
}
