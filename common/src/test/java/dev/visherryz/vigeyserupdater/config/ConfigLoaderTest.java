package dev.visherryz.vigeyserupdater.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @TempDir Path directory;

    @Test void bundledConfigurationLoadsAndMatchesCurrentAssetNames() throws Exception {
        UpdaterConfig config = ConfigLoader.load(directory);
        assertEquals(10, config.artifacts().size());
        Map<String, ArtifactConfig> artifacts = config.artifacts().stream()
                .collect(Collectors.toMap(ArtifactConfig::id, item -> item));
        assertTrue(Pattern.matches(artifacts.get("geyser-bukkit").assetRegex(), "Geyser-Spigot.jar"));
        assertTrue(Pattern.matches(artifacts.get("geyser-velocity").assetRegex(), "Geyser-Velocity.jar"));
        assertTrue(Pattern.matches(artifacts.get("geyser-model-engine").assetRegex(), "GeyserModelEngine-1.0.9.jar"));
        assertTrue(Pattern.matches(artifacts.get("geyser-model-engine-extension").assetRegex(), "GeyserModelEngineExtension-1.0.9.jar"));
        assertTrue(Pattern.matches(artifacts.get("geyser-utils-bukkit").assetRegex(), "geyserutils-spigot-1.0-SNAPSHOT.jar"));
        assertTrue(Pattern.matches(artifacts.get("geyser-utils-velocity").assetRegex(), "geyserutils-velocity-1.0-SNAPSHOT.jar"));
        assertTrue(Pattern.matches(artifacts.get("geyser-utils-extension").assetRegex(), "geyserutils-geyser-1.0-SNAPSHOT.jar"));
        assertTrue(Pattern.matches(artifacts.get("boar").assetRegex(), "boar-geyser.jar"));
        assertTrue(Pattern.matches(artifacts.get("floodgate-bukkit").assetRegex(), "floodgate-spigot.jar"));
        assertTrue(Pattern.matches(artifacts.get("floodgate-velocity").assetRegex(), "floodgate-velocity.jar"));
    }
}
