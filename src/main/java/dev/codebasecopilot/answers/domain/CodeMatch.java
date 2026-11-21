package dev.codebasecopilot.answers.domain;

public record CodeMatch(String filePath, int startLine, int endLine, String content, double score) {}
