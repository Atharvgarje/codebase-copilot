package dev.codebasecopilot.projects.api;

import dev.codebasecopilot.projects.domain.Project;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String repositoryUrl,
        String defaultBranch,
        Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getRepositoryUrl(),
                project.getDefaultBranch(), project.getCreatedAt());
    }
}
