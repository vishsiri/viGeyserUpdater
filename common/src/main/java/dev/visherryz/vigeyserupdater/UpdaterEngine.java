package dev.visherryz.vigeyserupdater;

import dev.visherryz.vigeyserupdater.config.ArtifactConfig;
import dev.visherryz.vigeyserupdater.config.ConfigLoader;
import dev.visherryz.vigeyserupdater.config.UpdaterConfig;
import dev.visherryz.vigeyserupdater.install.CrashSafeInstaller;
import dev.visherryz.vigeyserupdater.install.Hashing;
import dev.visherryz.vigeyserupdater.install.JarValidator;
import dev.visherryz.vigeyserupdater.net.SecureHttpClient;
import dev.visherryz.vigeyserupdater.source.ArtifactSource;
import dev.visherryz.vigeyserupdater.source.GitHubReleaseSource;
import dev.visherryz.vigeyserupdater.source.ModrinthSource;
import dev.visherryz.vigeyserupdater.source.RemoteArtifact;
import dev.visherryz.vigeyserupdater.state.InstalledArtifact;
import dev.visherryz.vigeyserupdater.state.StateStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class UpdaterEngine implements AutoCloseable {
    private final Path dataDirectory;
    private final PlatformKind platform;
    private final LogSink log;
    private final DestinationResolver destinations;
    private final CrashSafeInstaller installer;
    private final StateStore stateStore;
    private final ScheduledExecutorService executor;
    private final Map<String, UpdateStatus> statuses = new LinkedHashMap<>();
    private volatile UpdaterConfig config;
    private volatile ScheduledFuture<?> automaticTask;

    public UpdaterEngine(Path serverRoot, Path dataDirectory, PlatformKind platform, LogSink log) throws Exception {
        this.dataDirectory = dataDirectory;
        this.platform = platform;
        this.log = log;
        this.destinations = new DestinationResolver(serverRoot, platform);
        this.installer = new CrashSafeInstaller(dataDirectory.resolve("transactions"), log);
        this.installer.recoverAll();
        this.stateStore = new StateStore(dataDirectory.resolve("state.json"));
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "viGeyserUpdater-worker");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((thread1, error) -> log.error("Updater worker failed", error));
            return thread;
        });
        reload();
    }

    public synchronized void reload() throws Exception {
        UpdaterConfig loaded = ConfigLoader.load(dataDirectory);
        if (loaded.artifacts().stream().map(ArtifactConfig::id).distinct().count() != loaded.artifacts().size()) {
            throw new IllegalArgumentException("Artifact IDs must be unique");
        }
        this.config = loaded;
        statuses.clear();
        activeArtifacts().forEach(artifact -> statuses.put(artifact.id(),
                new UpdateStatus(artifact.id(), UpdateStatus.State.UNKNOWN, "not checked")));
        if (automaticTask != null) automaticTask.cancel(false);
        if (loaded.automatic()) {
            automaticTask = executor.scheduleWithFixedDelay(this::automaticRun,
                    loaded.initialDelaySeconds(), loaded.intervalSeconds(), TimeUnit.SECONDS);
        }
    }

    public CompletableFuture<List<UpdateStatus>> check(boolean install, String selectedId, Consumer<String> output) {
        return CompletableFuture.supplyAsync(() -> runCheck(install, selectedId, output), executor);
    }

    public synchronized List<UpdateStatus> snapshot() { return List.copyOf(statuses.values()); }

    private List<UpdateStatus> runCheck(boolean install, String selectedId, Consumer<String> output) {
        List<ArtifactConfig> artifacts = activeArtifacts();
        if (selectedId != null && !selectedId.equalsIgnoreCase("all")) {
            artifacts = artifacts.stream().filter(item -> item.id().equalsIgnoreCase(selectedId)).toList();
            if (artifacts.isEmpty()) {
                output.accept("Unknown or disabled artifact: " + selectedId);
                return snapshot();
            }
        }
        SecureHttpClient http = new SecureHttpClient(Set.copyOf(config.allowedHosts()), config.maxDownloadBytes(),
                config.connectTimeoutSeconds(), config.requestTimeoutSeconds(),
                config.retryAttempts(), config.retryDelayMillis());
        for (ArtifactConfig artifact : artifacts) {
            try {
                set(artifact.id(), UpdateStatus.State.CHECKING, "querying " + artifact.provider());
                ArtifactSource source = switch (artifact.provider().toLowerCase()) {
                    case "github-release" -> new GitHubReleaseSource(http);
                    case "modrinth" -> new ModrinthSource(http);
                    default -> throw new IllegalArgumentException("Unsupported provider: " + artifact.provider());
                };
                RemoteArtifact remote = source.resolve(artifact);
                Path target = destinations.resolve(artifact);
                InstalledArtifact installed = stateStore.get(artifact.id());
                boolean current = installed != null && installed.version().equals(remote.version()) && Files.exists(target);
                if (current) {
                    String actualHash = Hashing.sha256(target);
                    current = actualHash.equalsIgnoreCase(installed.sha256());
                }
                if (current) {
                    set(artifact.id(), UpdateStatus.State.UP_TO_DATE, remote.fileName());
                } else if (!install) {
                    set(artifact.id(), UpdateStatus.State.AVAILABLE, remote.fileName());
                } else {
                    install(artifact, remote, target, http);
                }
                output.accept(format(statuses.get(artifact.id())));
            } catch (Exception error) {
                set(artifact.id(), UpdateStatus.State.FAILED, error.getMessage());
                output.accept(format(statuses.get(artifact.id())));
                log.error("Update failed for " + artifact.id(), error);
            }
        }
        return snapshot();
    }

    private void install(ArtifactConfig artifact, RemoteArtifact remote, Path target, SecureHttpClient http) throws Exception {
        set(artifact.id(), UpdateStatus.State.INSTALLING, remote.fileName());
        Path downloads = dataDirectory.resolve("downloads");
        Files.createDirectories(downloads);
        Path temporary = Files.createTempFile(downloads, artifact.id() + "-", ".download");
        try {
            http.download(remote.downloadUri(), temporary, remote.expectedSize());
            String sha256 = Hashing.sha256(temporary);
            if (remote.expectedSha256() != null && !sha256.equalsIgnoreCase(remote.expectedSha256())) {
                throw new IllegalStateException("SHA-256 mismatch for " + remote.fileName());
            }
            JarValidator.validate(temporary);
            installer.install(artifact.id(), temporary, target);
            stateStore.put(artifact.id(), new InstalledArtifact(remote.version(), sha256, Instant.now().toEpochMilli()));
            set(artifact.id(), UpdateStatus.State.INSTALLED, remote.fileName() + "; restart required");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void automaticRun() {
        try {
            log.info("Running automatic update check");
            runCheck(config.applyAutomatically(), null, message -> log.info("[auto] " + message));
        } catch (Throwable error) {
            log.error("Automatic update check failed", error);
        }
    }

    private synchronized void set(String id, UpdateStatus.State state, String detail) {
        statuses.put(id, new UpdateStatus(id, state, detail == null ? "unknown error" : detail));
    }
    private List<ArtifactConfig> activeArtifacts() {
        return config.artifacts().stream().filter(item -> item.supports(platform)).toList();
    }
    public static String format(UpdateStatus status) {
        return status.id() + ": " + status.state().name().toLowerCase().replace('_', ' ') + " (" + status.detail() + ")";
    }
    @Override public void close() {
        if (automaticTask != null) automaticTask.cancel(false);
        executor.shutdown();
    }
}
