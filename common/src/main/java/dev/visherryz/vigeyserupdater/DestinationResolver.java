package dev.visherryz.vigeyserupdater;

import dev.visherryz.vigeyserupdater.config.ArtifactConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DestinationResolver {
    private final Path serverRoot;
    private final PlatformKind platform;

    public DestinationResolver(Path serverRoot, PlatformKind platform) throws IOException {
        this.serverRoot = serverRoot.toRealPath();
        this.platform = platform;
    }

    public Path resolve(ArtifactConfig config) throws IOException {
        String geyserFolder = platform == PlatformKind.VELOCITY ? "Geyser-Velocity" : "Geyser-Spigot";
        String expanded = config.destination().replace("${geyser}", "plugins/" + geyserFolder)
                .replace("${plugins}", "plugins");
        Path configured = serverRoot.resolve(expanded).normalize().toAbsolutePath();
        requireInsideRoot(configured);
        requireExistingAncestorInsideRoot(configured);
        if (Files.exists(configured) || config.existingRegex() == null || Files.notExists(configured.getParent())) return configured;
        Pattern pattern = Pattern.compile(config.existingRegex());
        List<Path> matches = new ArrayList<>();
        try (var files = Files.list(configured.getParent())) {
            files.filter(Files::isRegularFile).filter(path -> pattern.matcher(path.getFileName().toString()).matches())
                    .forEach(matches::add);
        }
        if (matches.size() > 1) throw new IOException("Multiple existing JARs match " + config.existingRegex() + " for " + config.id());
        return matches.isEmpty() ? configured : matches.getFirst();
    }

    private void requireInsideRoot(Path path) throws IOException {
        if (!path.startsWith(serverRoot) || path.equals(serverRoot)) {
            throw new IOException("Destination escapes the server directory: " + path);
        }
    }

    private void requireExistingAncestorInsideRoot(Path path) throws IOException {
        Path ancestor = path;
        while (ancestor != null && Files.notExists(ancestor)) ancestor = ancestor.getParent();
        if (ancestor == null || !ancestor.toRealPath().startsWith(serverRoot)) {
            throw new IOException("Destination resolves outside the server directory: " + path);
        }
    }
}
