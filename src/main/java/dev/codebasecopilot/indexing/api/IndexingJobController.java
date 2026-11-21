package dev.codebasecopilot.indexing.api;

import dev.codebasecopilot.indexing.application.IndexingJobService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IndexingJobController {

    private final IndexingJobService service;

    public IndexingJobController(IndexingJobService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/index-jobs")
    public ResponseEntity<IndexingJobResponse> requestIndex(@PathVariable UUID projectId) {
        var job = service.requestIndex(projectId);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/index-jobs/" + job.id()))
                .body(IndexingJobResponse.from(job));
    }

    @GetMapping("/index-jobs/{jobId}")
    public IndexingJobResponse get(@PathVariable UUID jobId) {
        return IndexingJobResponse.from(service.get(jobId));
    }

    @GetMapping("/projects/{projectId}/index-jobs")
    public List<IndexingJobResponse> listForProject(@PathVariable UUID projectId) {
        return service.listForProject(projectId).stream().map(IndexingJobResponse::from).toList();
    }
}
