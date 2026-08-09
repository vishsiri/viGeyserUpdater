package dev.visherryz.vigeyserupdater.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SecureHttpClient {
    private final HttpClient client;
    private final Set<String> allowedHosts;
    private final long maxDownloadBytes;
    private final Duration requestTimeout;
    private final String githubToken;
    private final int retryAttempts;
    private final long retryDelayMillis;

    public SecureHttpClient(Set<String> allowedHosts, long maxDownloadBytes, int connectTimeoutSeconds, int requestTimeoutSeconds) {
        this(allowedHosts, maxDownloadBytes, connectTimeoutSeconds, requestTimeoutSeconds, 3, 1_000);
    }

    public SecureHttpClient(Set<String> allowedHosts, long maxDownloadBytes, int connectTimeoutSeconds,
                            int requestTimeoutSeconds, int retryAttempts, long retryDelayMillis) {
        this.allowedHosts = new HashSet<>();
        allowedHosts.forEach(host -> this.allowedHosts.add(host.toLowerCase(Locale.ROOT)));
        this.maxDownloadBytes = maxDownloadBytes;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        if (retryAttempts < 1 || retryAttempts > 5) throw new IllegalArgumentException("retryAttempts must be between 1 and 5");
        if (retryDelayMillis < 0) throw new IllegalArgumentException("retryDelayMillis cannot be negative");
        this.retryAttempts = retryAttempts;
        this.retryDelayMillis = retryDelayMillis;
        this.githubToken = System.getenv("GITHUB_TOKEN");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public JsonElement getJson(URI uri) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = sendFollowingRedirects(uri, "application/json");
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("JSON response is too large");
            return JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public void download(URI uri, Path destination, long advertisedSize) throws IOException, InterruptedException {
        if (advertisedSize > maxDownloadBytes) throw new IOException("Artifact exceeds configured maximum size");
        HttpResponse<InputStream> response = sendFollowingRedirects(uri, "application/octet-stream");
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (contentLength > maxDownloadBytes) throw new IOException("Download exceeds configured maximum size");
        try (InputStream input = response.body();
             FileChannel output = FileChannel.open(destination, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] block = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(block)) >= 0) {
                total += read;
                if (total > maxDownloadBytes) throw new IOException("Download exceeded configured maximum size");
                output.write(ByteBuffer.wrap(block, 0, read));
            }
            output.force(true);
            if (advertisedSize >= 0 && total != advertisedSize) {
                throw new IOException("Downloaded size mismatch: expected " + advertisedSize + ", got " + total);
            }
        }
    }

    private HttpResponse<InputStream> sendFollowingRedirects(URI initial, String accept) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                return sendRedirectChain(initial, accept);
            } catch (NonRetryableIOException error) {
                throw error;
            } catch (IOException error) {
                lastFailure = error;
                if (attempt < retryAttempts) backoff(attempt);
            }
        }
        throw new IOException("Request failed after " + retryAttempts + " attempts: "
                + (lastFailure == null ? "unknown error" : lastFailure.getMessage()), lastFailure);
    }

    private HttpResponse<InputStream> sendRedirectChain(URI initial, String accept) throws IOException, InterruptedException {
        URI current = initial;
        for (int redirect = 0; redirect <= 5; redirect++) {
            validate(current);
            HttpRequest.Builder builder = HttpRequest.newBuilder(current).timeout(requestTimeout)
                    .header("Accept", accept).header("User-Agent", "viGeyserUpdater/1.0").GET();
            if (githubToken != null && !githubToken.isBlank() && current.getHost().endsWith("github.com")) {
                builder.header("Authorization", "Bearer " + githubToken);
            }
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 200 && status < 300) return response;
            if (status >= 300 && status < 400) {
                response.body().close();
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("Redirect without Location"));
                current = current.resolve(location);
                continue;
            }
            if (status == 429 || status >= 500) {
                try (InputStream ignored = response.body()) {
                    throw new IOException("Retryable HTTP " + status + " from " + current.getHost());
                }
            }
            try (InputStream ignored = response.body()) {
                throw new NonRetryableIOException("HTTP " + status + " from " + current.getHost());
            }
        }
        throw new NonRetryableIOException("Too many redirects");
    }

    private void validate(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new NonRetryableIOException("Only HTTPS downloads are allowed");
        String host = uri.getHost();
        if (host == null || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new NonRetryableIOException("Host is not allowlisted: " + host);
        }
        if (uri.getUserInfo() != null) throw new NonRetryableIOException("User info is not allowed in URLs");
    }

    private void backoff(int failedAttempt) throws InterruptedException {
        if (retryDelayMillis == 0) return;
        long multiplier = 1L << Math.min(failedAttempt - 1, 10);
        Thread.sleep(Math.min(60_000, Math.multiplyExact(retryDelayMillis, multiplier)));
    }

    private static final class NonRetryableIOException extends IOException {
        private NonRetryableIOException(String message) { super(message); }
    }
}
