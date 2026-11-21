package dev.codebasecopilot.projects.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "https://[^\\s]+", message = "must be an HTTPS repository URL")
        String repositoryUrl,
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "[A-Za-z0-9._/-]+", message = "contains unsupported branch characters")
        String defaultBranch) {}
