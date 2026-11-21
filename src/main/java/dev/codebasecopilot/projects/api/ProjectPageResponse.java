package dev.codebasecopilot.projects.api;

import java.util.List;

public record ProjectPageResponse(
        List<ProjectResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {}
