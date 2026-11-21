package dev.codebasecopilot.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/** Provider adapter for OpenAI-compatible chat and embedding APIs, including local Ollama. */
@Component
public class AiModelClient {

    private static final Pattern CITATION_REFERENCE = Pattern.compile("\\[(\\d+)]");
    private static final int MAX_RESPONSE_TOKENS = 512;

    private final RestClient client;
    private final String apiKey;
    private final boolean enabled;
    private final String chatModel;
    private final String embeddingModel;

    public AiModelClient(
            RestClient.Builder builder,
            @Value("${app.ai.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${app.ai.enabled:true}") boolean enabled,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.chat-model:qwen2.5-coder:1.5b}") String chatModel,
            @Value("${app.ai.embedding-model:nomic-embed-text}") String embeddingModel,
            @Value("${app.ai.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${app.ai.read-timeout:PT45S}") Duration readTimeout) {
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            configured.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        configured.requestFactory(requestFactory);
        this.client = configured.build();
    }

    public boolean isConfigured() {
        return enabled;
    }

    public List<float[]> embed(List<String> inputs) {
        if (!isConfigured() || inputs.isEmpty()) {
            return java.util.Collections.nCopies(inputs.size(), null);
        }
        var response = client.post().uri("/embeddings")
                .body(new EmbeddingRequest(embeddingModel, inputs))
                .retrieve().body(new ParameterizedTypeReference<EmbeddingResponse>() {});
        if (response == null || response.data() == null) {
            throw new IllegalStateException("The embedding provider returned an empty response");
        }
        List<EmbeddingData> ordered = response.data().stream().sorted(Comparator.comparingInt(EmbeddingData::index)).toList();
        if (ordered.size() != inputs.size()) {
            throw new IllegalStateException("The embedding provider returned an unexpected vector count");
        }
        List<float[]> vectors = new ArrayList<>(ordered.size());
        for (EmbeddingData data : ordered) {
            if (data.embedding() == null || data.embedding().length != 768) {
                throw new IllegalStateException("The configured embedding model must return 768 dimensions");
            }
            vectors.add(data.embedding());
        }
        return vectors;
    }

    public ChatResult answer(String systemPrompt, String userPrompt, int citationCount) {
        if (!isConfigured()) {
            throw new AiNotConfiguredException();
        }
        var response = client.post().uri("/chat/completions")
                .body(new ChatRequest(chatModel, List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userPrompt)), MAX_RESPONSE_TOKENS))
                .retrieve().body(new ParameterizedTypeReference<ChatResponse>() {});
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new IllegalStateException("The chat provider returned an empty response");
        }
        String content = response.choices().getFirst().message().content();
        Matcher references = CITATION_REFERENCE.matcher(content);
        while (references.find()) {
            int citationNumber = Integer.parseInt(references.group(1));
            if (citationNumber < 1 || citationNumber > citationCount) {
                throw new InvalidCitationException();
            }
        }
        Usage usage = response.usage();
        return new ChatResult(content, response.model() == null ? chatModel : response.model(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                usage == null ? null : usage.totalTokens());
    }

    public static class AiNotConfiguredException extends RuntimeException {}
    public static class InvalidCitationException extends RuntimeException {}
    public record ChatResult(String content, String model, Integer promptTokens,
                             Integer completionTokens, Integer totalTokens) {}

    private record EmbeddingRequest(String model, List<String> input) {}
    private record ChatRequest(String model, List<ChatMessage> messages,
                              @JsonProperty("max_tokens") int maxTokens) {}
    private record ChatMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<EmbeddingData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(float[] embedding, int index) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices, String model, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(ChatMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {}
}
