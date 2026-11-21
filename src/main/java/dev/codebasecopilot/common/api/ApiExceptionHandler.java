package dev.codebasecopilot.common.api;

import dev.codebasecopilot.projects.application.ProjectService.DuplicateRepositoryException;
import dev.codebasecopilot.projects.api.ProjectController.InvalidProjectPageRequestException;
import dev.codebasecopilot.indexing.application.IndexingJobService.IndexingJobNotFoundException;
import dev.codebasecopilot.indexing.application.IndexingJobService.ProjectNotFoundException;
import dev.codebasecopilot.answers.application.AnswerService.NoRelevantCodeException;
import dev.codebasecopilot.answers.application.AnswerService;
import dev.codebasecopilot.answers.application.AnswerService.ProjectNotIndexedException;
import dev.codebasecopilot.ai.AiModelClient.AiNotConfiguredException;
import dev.codebasecopilot.ai.AiModelClient.InvalidCitationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateRepositoryException.class)
    public ResponseEntity<ApiError> duplicateRepository() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                Instant.now(), 409, "REPOSITORY_ALREADY_REGISTERED", "This repository is already registered.", Map.of()));
    }

    @ExceptionHandler(InvalidProjectPageRequestException.class)
    public ResponseEntity<ApiError> invalidProjectPage() {
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100.", Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "VALIDATION_FAILED", "The request contains invalid fields.", fields));
    }

    @ExceptionHandler({ProjectNotFoundException.class, IndexingJobNotFoundException.class,
            AnswerService.ProjectNotFoundException.class,
            dev.codebasecopilot.projects.application.ProjectService.ProjectNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException exception) {
        String resource = exception instanceof IndexingJobNotFoundException ? "indexing job" : "project";
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(
                Instant.now(), 404, "RESOURCE_NOT_FOUND", "The requested " + resource + " was not found.", Map.of()));
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    public ResponseEntity<ApiError> aiNotConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                Instant.now(), 503, "AI_PROVIDER_NOT_CONFIGURED", "Configure an AI provider before asking questions.", Map.of()));
    }

    @ExceptionHandler(ProjectNotIndexedException.class)
    public ResponseEntity<ApiError> projectNotIndexed() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                Instant.now(), 409, "PROJECT_NOT_INDEXED", "Index this project before asking questions.", Map.of()));
    }

    @ExceptionHandler(NoRelevantCodeException.class)
    public ResponseEntity<ApiError> noRelevantCode() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError(
                Instant.now(), 422, "NO_RELEVANT_CODE", "No matching code was found for this question.", Map.of()));
    }

    @ExceptionHandler({RestClientException.class, InvalidCitationException.class})
    public ResponseEntity<ApiError> aiProviderUnavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(
                Instant.now(), 502, "AI_PROVIDER_UNAVAILABLE", "The AI provider did not return a usable response.", Map.of()));
    }
}
