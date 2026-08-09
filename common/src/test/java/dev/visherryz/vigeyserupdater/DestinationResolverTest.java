package dev.visherryz.vigeyserupdater;

import dev.visherryz.vigeyserupdater.config.ArtifactConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DestinationResolverTest {
    @TempDir Path root;
    @Test void resolvesExistingVersionedJar() throws Exception {
        Path plugins = Files.createDirectories(root.resolve("plugins"));
        Path existing = Files.writeString(plugins.resolve("Tool-1.2.jar"), "x");
        ArtifactConfig config = config("${plugins}/Tool.jar", "Tool-.+\\.jar");
        assertTrue(Files.isSameFile(existing, new DestinationResolver(root, PlatformKind.BUKKIT).resolve(config)));
    }
    @Test void rejectsTraversal() throws Exception {
        ArtifactConfig config = config("../outside.jar", null);
        assertThrows(IOException.class, () -> new DestinationResolver(root, PlatformKind.BUKKIT).resolve(config));
    }
    private ArtifactConfig config(String destination, String regex) {
        return new ArtifactConfig("test", true, List.of(PlatformKind.BUKKIT), "github-release",
                "owner/repo", null, ".*", destination, regex, List.of("release"));
    }
}
