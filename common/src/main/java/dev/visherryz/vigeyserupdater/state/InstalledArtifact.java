package dev.visherryz.vigeyserupdater.state;

public record InstalledArtifact(String version, String sha256, long installedAtEpochMillis) {}
