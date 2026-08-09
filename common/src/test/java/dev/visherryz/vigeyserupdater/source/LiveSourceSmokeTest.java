package dev.visherryz.vigeyserupdater.source;

import dev.visherryz.vigeyserupdater.PlatformKind;
import dev.visherryz.vigeyserupdater.LogSink;
import dev.visherryz.vigeyserupdater.config.ArtifactConfig;
import dev.visherryz.vigeyserupdater.install.CrashSafeInstaller;
import dev.visherryz.vigeyserupdater.install.JarValidator;
import dev.visherryz.vigeyserupdater.net.SecureHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "VI_LIVE_UPDATE_TEST", matches = "true")
class LiveSourceSmokeTest {
    @TempDir Path directory;
    private final SecureHttpClient http = new SecureHttpClient(Set.of("api.github.com", "github.com",
            "release-assets.githubusercontent.com", "objects.githubusercontent.com", "api.modrinth.com", "cdn.modrinth.com"),
            128L * 1024 * 1024, 10, 120);

    @Test void resolvesAndDownloadsRealGitHubAsset() throws Exception {
        ArtifactConfig config = new ArtifactConfig("utils-velocity", true, List.of(PlatformKind.VELOCITY),
                "github-release", "GeyserExtensionists/GeyserUtils", null,
                "geyserutils-velocity-.+\\.jar", "plugins/GeyserUtils.jar", null, List.of("release"));
        RemoteArtifact remote = new GitHubReleaseSource(http).resolve(config);
        Path file = directory.resolve("asset.jar");
        http.download(remote.downloadUri(), file, remote.expectedSize());
        JarValidator.validate(file);
        Path target = directory.resolve("plugins/GeyserUtils.jar");
        new CrashSafeInstaller(directory.resolve("transactions"), new SilentLog()).install("utils-velocity", file, target);
        JarValidator.validate(target);
        assertTrue(remote.version().startsWith("latest:"));
    }

    @Test void resolvesCurrentBoarFromModrinth() throws Exception {
        ArtifactConfig config = new ArtifactConfig("boar", true, List.of(PlatformKind.BUKKIT),
                "modrinth", null, "boar", "boar-geyser\\.jar", "plugins/Boar.jar", null,
                List.of("release", "beta", "alpha"));
        RemoteArtifact remote = new ModrinthSource(http).resolve(config);
        assertTrue(remote.downloadUri().getHost().equals("cdn.modrinth.com"));
    }

    @Test void resolvesCurrentGeyserModelEngineExtension() throws Exception {
        ArtifactConfig config = new ArtifactConfig("model-engine-extension", true, List.of(PlatformKind.BUKKIT),
                "github-release", "GeyserExtensionists/GeyserModelEngine", null,
                "GeyserModelEngineExtension-.+\\.jar", "plugins/Geyser/extension.jar", null, List.of("release"));
        RemoteArtifact remote = new GitHubReleaseSource(http).resolve(config);
        assertTrue(remote.fileName().startsWith("GeyserModelEngineExtension-"));
    }

    private static final class SilentLog implements LogSink {
        public void info(String message) {}
        public void warn(String message) {}
        public void error(String message, Throwable error) {}
    }
}
