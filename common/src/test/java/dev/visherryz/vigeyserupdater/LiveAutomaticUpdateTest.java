package dev.visherryz.vigeyserupdater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "VI_LIVE_UPDATE_TEST", matches = "true")
class LiveAutomaticUpdateTest {
    @TempDir Path serverRoot;

    @Test void automaticSchedulerDownloadsInstallsAndThenReportsCurrent() throws Exception {
        Path data = Files.createDirectories(serverRoot.resolve("plugins/viGeyserUpdater"));
        Files.writeString(data.resolve("config.yml"), """
                automatic:
                  enabled: true
                  initial-delay-seconds: 0
                  interval-minutes: 5
                  apply: true
                network:
                  max-download-mib: 8
                  connect-timeout-seconds: 30
                  request-timeout-seconds: 120
                  allowed-hosts: [api.github.com, github.com, release-assets.githubusercontent.com, objects.githubusercontent.com]
                artifacts:
                  - id: geyser-utils-velocity
                    enabled: true
                    platforms: [VELOCITY]
                    provider: github-release
                    repository: GeyserExtensionists/GeyserUtils
                    asset-regex: 'geyserutils-velocity-.+\\.jar'
                    destination: '${plugins}/GeyserUtils.jar'
                    release-types: [release]
                """);
        try (UpdaterEngine engine = new UpdaterEngine(serverRoot, data, PlatformKind.VELOCITY, new SilentLog())) {
            Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
            while (Instant.now().isBefore(deadline)
                    && engine.snapshot().stream().noneMatch(status -> status.state() == UpdateStatus.State.INSTALLED)) {
                Thread.sleep(25);
            }
            assertTrue(Files.exists(serverRoot.resolve("plugins/GeyserUtils.jar")),
                    "automatic update did not install the JAR: " + engine.snapshot());
            List<UpdateStatus> checked = engine.check(false, null, message -> {}).get();
            assertEquals(UpdateStatus.State.UP_TO_DATE, checked.getFirst().state());
        }
    }

    private static final class SilentLog implements LogSink {
        public void info(String message) {} public void warn(String message) {}
        public void error(String message, Throwable error) {}
    }
}
