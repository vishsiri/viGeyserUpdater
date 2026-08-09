package dev.visherryz.vigeyserupdater.source;

import dev.visherryz.vigeyserupdater.config.ArtifactConfig;

public interface ArtifactSource {
    RemoteArtifact resolve(ArtifactConfig config) throws Exception;
}
