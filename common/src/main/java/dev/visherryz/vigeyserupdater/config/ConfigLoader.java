package dev.visherryz.vigeyserupdater.config;

import dev.visherryz.vigeyserupdater.PlatformKind;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigLoader {
    private ConfigLoader() {}

    public static UpdaterConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path configPath = dataDirectory.resolve("config.yml");
        if (Files.notExists(configPath)) {
            try (InputStream input = ConfigLoader.class.getResourceAsStream("/config.yml")) {
                if (input == null) throw new IOException("Bundled config.yml is missing");
                Files.copy(input, configPath);
            }
        }

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(2_000_000);
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(configPath)) {
            root = new Yaml(new SafeConstructor(options)).load(input);
        }
        if (root == null) throw new IllegalArgumentException("config.yml is empty");
        Map<String, Object> auto = map(root, "automatic");
        Map<String, Object> network = map(root, "network");
        List<ArtifactConfig> artifacts = new ArrayList<>();
        for (Object raw : list(root, "artifacts")) {
            if (!(raw instanceof Map<?, ?> any)) throw new IllegalArgumentException("Every artifact must be a map");
            @SuppressWarnings("unchecked") Map<String, Object> item = (Map<String, Object>) any;
            List<PlatformKind> platforms = strings(item.get("platforms")).stream()
                    .map(value -> PlatformKind.valueOf(value.toUpperCase(Locale.ROOT))).toList();
            artifacts.add(new ArtifactConfig(
                    text(item, "id"), bool(item, "enabled", true), platforms,
                    text(item, "provider"), nullableText(item.get("repository")),
                    nullableText(item.get("project")), text(item, "asset-regex"),
                    text(item, "destination"), nullableText(item.get("existing-regex")),
                    strings(item.getOrDefault("release-types", List.of("release", "beta", "alpha")))
            ));
        }
        long intervalMinutes = number(auto, "interval-minutes", 360);
        if (intervalMinutes < 5) throw new IllegalArgumentException("automatic.interval-minutes must be at least 5");
        long maxMib = number(network, "max-download-mib", 128);
        int retryAttempts = (int) number(network, "retry-attempts", 3);
        long retryDelaySeconds = number(network, "retry-delay-seconds", 2);
        if (retryAttempts < 1 || retryAttempts > 5) throw new IllegalArgumentException("network.retry-attempts must be between 1 and 5");
        if (retryDelaySeconds < 0 || retryDelaySeconds > 60) throw new IllegalArgumentException("network.retry-delay-seconds must be between 0 and 60");
        return new UpdaterConfig(
                bool(auto, "enabled", true), number(auto, "initial-delay-seconds", 30),
                Math.multiplyExact(intervalMinutes, 60), bool(auto, "apply", true),
                Math.multiplyExact(maxMib, 1024L * 1024L),
                (int) number(network, "connect-timeout-seconds", 10),
                (int) number(network, "request-timeout-seconds", 60),
                retryAttempts, Math.multiplyExact(retryDelaySeconds, 1000),
                strings(network.get("allowed-hosts")), List.copyOf(artifacts));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(key + " must be a map");
        return (Map<String, Object>) value;
    }
    private static List<?> list(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException(key + " must be a list");
        return values;
    }
    private static String text(Map<String, Object> map, String key) {
        String value = nullableText(map.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
    private static String nullableText(Object value) { return value == null ? null : value.toString(); }
    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key); return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }
    private static long number(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key); return value == null ? fallback : Long.parseLong(value.toString());
    }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected a list, got " + value);
        return list.stream().map(Object::toString).toList();
    }
}
