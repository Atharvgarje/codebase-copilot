package dev.codebasecopilot.indexing.application;

import dev.codebasecopilot.indexing.domain.IndexingJobStore;
import dev.codebasecopilot.projects.domain.ProjectRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.indexing.worker.enabled", havingValue = "true", matchIfMissing = true)
public class IndexingWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexingWorker.class);
    private final String workerId = UUID.randomUUID().toString();
    private final IndexingJobStore jobs;
    private final ProjectRepository projects;
    private final RepositoryIndexer indexer;

    public IndexingWorker(IndexingJobStore jobs, ProjectRepository projects, RepositoryIndexer indexer) {
        this.jobs = jobs;
        this.projects = projects;
        this.indexer = indexer;
    }

    @Scheduled(fixedDelayString = "${app.indexing.poll-delay-ms:2000}")
    public void processOneJob() {
        jobs.claimNext(workerId).ifPresent(job -> {
            try {
                var project = projects.findById(job.projectId())
                        .orElseThrow(() -> new RepositoryIndexer.IndexingFailure("PROJECT_NOT_FOUND"));
                String revision = indexer.index(project, () -> {
                    if (!jobs.renewLease(job.id(), workerId)) {
                        throw new RepositoryIndexer.IndexingFailure("JOB_LEASE_LOST");
                    }
                });
                if (!jobs.complete(job.id(), job.projectId(), workerId, revision)) {
                    log.warn("Indexing lease was lost before job completion; jobId={}", job.id());
                }
            } catch (RepositoryIndexer.IndexingFailure failure) {
                boolean retryable = "REPOSITORY_FETCH_FAILED".equals(failure.code());
                jobs.markFailed(job.id(), workerId, failure.code(), retryable);
                log.warn("Indexing job failed; jobId={}, code={}", job.id(), failure.code());
            } catch (Exception failure) {
                jobs.markFailed(job.id(), workerId, "INDEXING_FAILED", true);
                log.warn("Indexing job failed unexpectedly; jobId={}, type={}", job.id(), failure.getClass().getSimpleName());
            }
        });
    }
}
