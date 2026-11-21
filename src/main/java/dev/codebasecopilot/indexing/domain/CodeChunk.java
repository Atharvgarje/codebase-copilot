package dev.codebasecopilot.indexing.domain;

import java.util.UUID;

public record CodeChunk(
        UUID id,
        UUID projectId,
        String revision,
        String filePath,
        int startLine,
        int endLine,
        String content,
        String contentSha256) {}
