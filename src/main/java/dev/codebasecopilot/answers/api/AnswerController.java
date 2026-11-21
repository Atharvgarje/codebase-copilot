package dev.codebasecopilot.answers.api;

import dev.codebasecopilot.answers.application.AnswerService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/questions")
public class AnswerController {

    private final AnswerService service;

    public AnswerController(AnswerService service) {
        this.service = service;
    }

    @PostMapping
    public AnswerResponse ask(@PathVariable UUID projectId, @Valid @RequestBody AskRequest request) {
        return service.ask(projectId, request.question().trim());
    }
}
