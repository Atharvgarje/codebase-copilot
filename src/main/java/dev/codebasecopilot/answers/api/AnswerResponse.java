package dev.codebasecopilot.answers.api;

import java.util.List;

public record AnswerResponse(
        String answer,
        String indexedRevision,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        List<AnswerCitation> citations) {}
