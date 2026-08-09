package dev.visherryz.vigeyserupdater.source;

import java.net.URI;

public record RemoteArtifact(String version, String fileName, URI downloadUri, long expectedSize, String expectedSha256) {}
