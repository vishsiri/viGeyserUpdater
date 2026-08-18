package dev.visherryz.vigeyserupdater.source;

import dev.visherryz.vigeyserupdater.PlatformKind;
import dev.visherryz.vigeyserupdater.LogSink;
import dev.visherryz.vigeyserupdater.config.ArtifactConfig;
import dev.visherryz.vigeyserupdater.install.CrashSafeInstaller;
import dev.visherryz.vigeyserupdater.install.Hashing;
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

    @Test void downloadsAndVerifiesCurrentFloodgateArtifact() throws Exception {
        SecureHttpClient geyserHttp = new SecureHttpClient(Set.of("download.geysermc.org"),
                128L * 1024 * 1024, 10, 120);
        ArtifactConfig config = new ArtifactConfig("floodgate-bukkit", true, List.of(PlatformKind.BUKKIT),
                "geyser-downloads", null, "floodgate", "floodgate-spigot\\.jar",
                "plugins/floodgate.jar", null, List.of("release"));
        RemoteArtifact remote = new GeyserDownloadSource(geyserHttp).resolve(config);
        Path file = directory.resolve("floodgate.jar");
        geyserHttp.download(remote.downloadUri(), file, remote.expectedSize());
        JarValidator.validate(file);
        assertTrue(remote.version().matches("2\\.2\\.\\d+:\\d+:spigot"));
        assertTrue(Hashing.sha256(file).equalsIgnoreCase(remote.expectedSha256()));
    }

    @Test void downloadsAndVerifiesCurrentGeyserAndResolvesBothPlatforms() throws Exception {
        SecureHttpClient geyserHttp = new SecureHttpClient(Set.of("download.geysermc.org"),
                128L * 1024 * 1024, 10, 120);
        ArtifactConfig spigotConfig = new ArtifactConfig("geyser-bukkit", true, List.of(PlatformKind.BUKKIT),
                "geyser-downloads", null, "geyser", "Geyser-Spigot\\.jar",
                "plugins/Geyser-Spigot.jar", null, List.of("release"));
        ArtifactConfig velocityConfig = new ArtifactConfig("geyser-velocity", true, List.of(PlatformKind.VELOCITY),
                "geyser-downloads", null, "geyser", "Geyser-Velocity\\.jar",
                "plugins/Geyser-Velocity.jar", null, List.of("release"));

        RemoteArtifact spigot = new GeyserDownloadSource(geyserHttp).resolve(spigotConfig);
        RemoteArtifact velocity = new GeyserDownloadSource(geyserHttp).resolve(velocityConfig);
        Path file = directory.resolve("Geyser-Spigot.jar");
        geyserHttp.download(spigot.downloadUri(), file, spigot.expectedSize());

        JarValidator.validate(file);
        assertTrue(spigot.version().matches("\\d+\\.\\d+\\.\\d+:\\d+:spigot"));
        assertTrue(Hashing.sha256(file).equalsIgnoreCase(spigot.expectedSha256()));
        assertTrue(velocity.version().endsWith(":velocity"));
        assertTrue(velocity.fileName().equals("Geyser-Velocity.jar"));
    }

    private static final class SilentLog implements LogSink {
        public void info(String message) {}
        public void warn(String message) {}
        public void error(String message, Throwable error) {}
    }
}
