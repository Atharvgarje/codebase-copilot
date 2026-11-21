package dev.codebasecopilot.projects.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findAllByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByRepositoryUrl(String repositoryUrl);
}
