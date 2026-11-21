package dev.codebasecopilot.projects.api;

import dev.codebasecopilot.projects.application.ProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        var project = projectService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.getId()))
                .body(ProjectResponse.from(project));
    }

    @GetMapping
    public ProjectPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidProjectPageRequestException();
        }
        var results = projectService.list(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new ProjectPageResponse(results.getContent().stream().map(ProjectResponse::from).toList(),
                results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId) {
        projectService.delete(projectId);
        return ResponseEntity.noContent().build();
    }

    public static class InvalidProjectPageRequestException extends RuntimeException {}
}
