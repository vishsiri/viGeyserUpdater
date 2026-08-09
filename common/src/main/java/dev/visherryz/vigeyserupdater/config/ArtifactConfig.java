package dev.visherryz.vigeyserupdater.config;

import dev.visherryz.vigeyserupdater.PlatformKind;

import java.util.List;

public record ArtifactConfig(
        String id,
        boolean enabled,
        List<PlatformKind> platforms,
        String provider,
        String repository,
        String project,
        String assetRegex,
        String destination,
        String existingRegex,
        List<String> releaseTypes
) {
    public boolean supports(PlatformKind kind) {
        return enabled && platforms.contains(kind);
    }
}
