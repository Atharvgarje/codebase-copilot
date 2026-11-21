package dev.codebasecopilot.answers.api;

public record AnswerCitation(int number, String filePath, int startLine, int endLine, String snippet) {}
