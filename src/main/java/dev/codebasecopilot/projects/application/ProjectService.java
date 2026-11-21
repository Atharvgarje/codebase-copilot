package dev.codebasecopilot.projects.application;

import dev.codebasecopilot.projects.api.CreateProjectRequest;
import dev.codebasecopilot.projects.domain.Project;
import dev.codebasecopilot.projects.domain.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional
    public Project create(CreateProjectRequest request) {
        if (projects.existsByRepositoryUrl(request.repositoryUrl())) {
            throw new DuplicateRepositoryException();
        }

        try {
            return projects.saveAndFlush(new Project(
                    request.name().trim(), request.repositoryUrl().trim(), request.defaultBranch().trim()));
        } catch (DataIntegrityViolationException exception) {
            // The database constraint is the final guard against simultaneous duplicate requests.
            throw new DuplicateRepositoryException();
        }
    }

    @Transactional(readOnly = true)
    public Page<Project> list(Pageable pageable) {
        return projects.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public void delete(java.util.UUID projectId) {
        Project project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        projects.delete(project);
    }

    public static class DuplicateRepositoryException extends RuntimeException {}
    public static class ProjectNotFoundException extends RuntimeException {}
}
