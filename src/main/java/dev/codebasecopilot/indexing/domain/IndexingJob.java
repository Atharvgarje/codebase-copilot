package dev.codebasecopilot.indexing.domain;

import java.time.Instant;
import java.util.UUID;

public record IndexingJob(
        UUID id,
        UUID projectId,
        IndexingJobStatus status,
        int attempt,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String errorCode) {}
