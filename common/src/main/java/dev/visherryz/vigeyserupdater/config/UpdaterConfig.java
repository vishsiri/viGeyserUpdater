package dev.visherryz.vigeyserupdater.config;

import java.util.List;

public record UpdaterConfig(
        boolean automatic,
        long initialDelaySeconds,
        long intervalSeconds,
        boolean applyAutomatically,
        long maxDownloadBytes,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        int retryAttempts,
        long retryDelayMillis,
        List<String> allowedHosts,
        List<ArtifactConfig> artifacts
) {}
