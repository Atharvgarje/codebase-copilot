package dev.codebasecopilot.projects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "repository_url", nullable = false, length = 2048, unique = true)
    private String repositoryUrl;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Column(name = "indexed_revision", length = 128)
    private String indexedRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {}

    public Project(String name, String repositoryUrl, String defaultBranch) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.defaultBranch = defaultBranch;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public String getIndexedRevision() { return indexedRevision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
