package dev.codebasecopilot.indexing.application;

import dev.codebasecopilot.ai.AiModelClient;
import dev.codebasecopilot.indexing.domain.CodeChunk;
import dev.codebasecopilot.indexing.domain.CodeChunkStore;
import dev.codebasecopilot.projects.domain.Project;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class RepositoryIndexer {

    private static final Logger log = LoggerFactory.getLogger(RepositoryIndexer.class);
    private static final int CHUNK_LINES = 100;
    private static final int CHUNK_OVERLAP = 15;
    private static final int EMBEDDING_BATCH_SIZE = 32;
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".py", ".js", ".jsx", ".ts", ".tsx", ".go", ".rs",
            ".c", ".h", ".cpp", ".hpp", ".cs", ".php", ".rb", ".swift", ".scala", ".sql",
            ".xml", ".yml", ".yaml", ".json", ".toml", ".properties", ".md", ".gradle",
            ".sh", ".tf", ".html", ".css", ".scss", ".vue", ".svelte");
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", "node_modules", "vendor", "target", "build", "dist", "out", ".next", ".venv");
    private static final Set<String> IGNORED_FILES = Set.of(
            ".env", ".env.local", "id_rsa", "id_ed25519", "credentials", "secrets.yml", "secrets.yaml");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)([\\\"']?(?:password|passwd|secret|token|api[_-]?key|client[_-]?secret|access[_-]?key)[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s,;#\\\"']+");

    private final CodeChunkStore chunks;
    private final AiModelClient ai;
    private final int maxFiles;
    private final long maxTotalBytes;
    private final long maxFileBytes;

    public RepositoryIndexer(
            CodeChunkStore chunks,
            AiModelClient ai,
            @Value("${app.indexing.max-files:20000}") int maxFiles,
            @Value("${app.indexing.max-total-bytes:52428800}") long maxTotalBytes,
            @Value("${app.indexing.max-file-bytes:1048576}") long maxFileBytes) {
        this.chunks = chunks;
        this.ai = ai;
        this.maxFiles = maxFiles;
        this.maxTotalBytes = maxTotalBytes;
        this.maxFileBytes = maxFileBytes;
    }

    public String index(Project project, Runnable heartbeat) throws IOException {
        URI repository = validatePublicGitHubUrl(project.getRepositoryUrl());
        Path temporaryDirectory = Files.createTempDirectory("codebase-copilot-");
        Path checkout = temporaryDirectory.resolve("repository");
        try (Git git = Git.cloneRepository()
                .setURI(repository.toString())
                .setDirectory(checkout.toFile())
                .setBranch(project.getDefaultBranch())
                .setDepth(1)
                .setTimeout(600)
                .setCloneAllBranches(false)
                .call()) {
            String revision = git.getRepository().resolve("HEAD").name();
            indexFiles(project.getId(), revision, checkout, heartbeat);
            return revision;
        } catch (org.eclipse.jgit.api.errors.GitAPIException exception) {
            throw new IndexingFailure("REPOSITORY_FETCH_FAILED", exception);
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    private void indexFiles(UUID projectId, String revision, Path checkout, Runnable heartbeat) throws IOException {
        List<CodeChunk> pending = new ArrayList<>(EMBEDDING_BATCH_SIZE);
        long totalBytes = 0;
        int fileCount = 0;
        try (var paths = Files.walk(checkout)) {
            var iterator = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                String relativePath = checkout.relativize(file).toString().replace('\\', '/');
                if (isIgnored(relativePath)) continue;
                if (!isIndexable(relativePath)) continue;

                long size = Files.size(file);
                if (size > maxFileBytes) continue;
                totalBytes += size;
                if (totalBytes > maxTotalBytes) {
                    throw new IndexingFailure("REPOSITORY_SIZE_LIMIT_EXCEEDED");
                }
                if (++fileCount > maxFiles) {
                    throw new IndexingFailure("REPOSITORY_FILE_LIMIT_EXCEEDED");
                }

                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int start = 0; start < lines.size(); start += CHUNK_LINES - CHUNK_OVERLAP) {
                    int end = Math.min(lines.size(), start + CHUNK_LINES);
                    String content = redactSecrets(String.join("\n", lines.subList(start, end)));
                    if (content.isBlank()) continue;
                    pending.add(new CodeChunk(UUID.randomUUID(), projectId, revision, relativePath,
                            start + 1, end, content, sha256(content)));
                    if (pending.size() >= EMBEDDING_BATCH_SIZE) {
                        persistBatch(pending, heartbeat);
                        pending.clear();
                    }
                    if (end == lines.size()) break;
                }
            }
        }
        if (!pending.isEmpty()) persistBatch(pending, heartbeat);
    }

    private void persistBatch(List<CodeChunk> batch, Runnable heartbeat) {
        heartbeat.run();
        List<float[]> vectors;
        try {
            vectors = ai.embed(batch.stream().map(CodeChunk::content).toList());
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Embedding batch failed; storing searchable text without vectors: {}", exception.getClass().getSimpleName());
            vectors = java.util.Collections.nCopies(batch.size(), null);
        }
        chunks.saveAll(batch, vectors);
        heartbeat.run();
    }

    private static URI validatePublicGitHubUrl(String repositoryUrl) {
        try {
            URI uri = URI.create(repositoryUrl);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/$", "");
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !path.matches("/[^/]+/[^/]+(?:\\.git)?")) {
                throw new IndexingFailure("UNSUPPORTED_REPOSITORY_URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IndexingFailure("UNSUPPORTED_REPOSITORY_URL", exception);
        }
    }

    private static boolean isIndexable(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        boolean safeEnvTemplate = fileName.contains("example") || fileName.contains("sample") || fileName.contains("template");
        if (fileName.startsWith(".env") && !safeEnvTemplate) return false;
        if (IGNORED_FILES.contains(fileName) || fileName.endsWith(".pem") || fileName.endsWith(".key")
                || fileName.endsWith(".p12") || fileName.endsWith(".pfx")
                || fileName.contains("credential") || fileName.contains("secret")) return false;
        if (fileName.equals("dockerfile") || fileName.equals("makefile")) return true;
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && CODE_EXTENSIONS.contains(fileName.substring(dot));
    }

    private static boolean isIgnored(String path) {
        for (String segment : path.split("/")) {
            if (IGNORED_DIRECTORIES.contains(segment.toLowerCase())) return true;
        }
        return false;
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static String redactSecrets(String content) {
        return SECRET_ASSIGNMENT.matcher(content).replaceAll("$1[REDACTED]");
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { log.warn("Could not remove temporary indexing file"); }
            });
        } catch (IOException exception) {
            log.warn("Could not clean temporary repository directory");
        }
    }

    public static class IndexingFailure extends RuntimeException {
        private final String code;
        public IndexingFailure(String code) { super(code); this.code = code; }
        public IndexingFailure(String code, Throwable cause) { super(code, cause); this.code = code; }
        public String code() { return code; }
    }
}
