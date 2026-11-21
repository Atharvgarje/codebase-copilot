package dev.codebasecopilot.indexing.application;

import dev.codebasecopilot.indexing.domain.IndexingJob;
import dev.codebasecopilot.indexing.domain.IndexingJobStore;
import dev.codebasecopilot.projects.domain.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexingJobService {

    private final IndexingJobStore jobs;
    private final ProjectRepository projects;

    public IndexingJobService(IndexingJobStore jobs, ProjectRepository projects) {
        this.jobs = jobs;
        this.projects = projects;
    }

    @Transactional
    public IndexingJob requestIndex(UUID projectId) {
        if (!projects.existsById(projectId)) {
            throw new ProjectNotFoundException();
        }
        return jobs.createOrGetActive(projectId);
    }

    @Transactional(readOnly = true)
    public IndexingJob get(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(IndexingJobNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<IndexingJob> listForProject(UUID projectId) {
        if (!projects.existsById(projectId)) {
            throw new ProjectNotFoundException();
        }
        return jobs.findByProjectId(projectId);
    }

    public static class ProjectNotFoundException extends RuntimeException {}
    public static class IndexingJobNotFoundException extends RuntimeException {}
}
