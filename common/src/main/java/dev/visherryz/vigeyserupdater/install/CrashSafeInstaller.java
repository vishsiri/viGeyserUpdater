package dev.visherryz.vigeyserupdater.install;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.visherryz.vigeyserupdater.LogSink;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class CrashSafeInstaller {
    private static final Gson GSON = new Gson();
    private final Path transactionDirectory;
    private final LogSink log;
    private final FaultInjector faults;

    public CrashSafeInstaller(Path transactionDirectory, LogSink log) {
        this(transactionDirectory, log, FaultInjector.NONE);
    }

    CrashSafeInstaller(Path transactionDirectory, LogSink log, FaultInjector faults) {
        this.transactionDirectory = transactionDirectory;
        this.log = log;
        this.faults = faults;
    }

    public void install(String id, Path downloaded, Path target) throws Exception {
        Files.createDirectories(transactionDirectory);
        Files.createDirectories(target.getParent());
        Path pending = target.resolveSibling(target.getFileName() + ".viupdater.pending");
        Path backup = target.resolveSibling(target.getFileName() + ".viupdater.bak");
        Path journal = journal(id);
        if (Files.exists(journal) || Files.exists(pending) || Files.exists(backup)) recoverOne(journal, target, pending, backup);

        move(downloaded, pending, false);
        writeJournal(journal, target, pending, backup, "PREPARED");
        faults.checkpoint("PREPARED");
        if (Files.exists(target)) move(target, backup, true);
        writeJournal(journal, target, pending, backup, "BACKED_UP");
        faults.checkpoint("BACKED_UP");
        move(pending, target, true);
        writeJournal(journal, target, pending, backup, "INSTALLED");
        faults.checkpoint("INSTALLED");
        Files.deleteIfExists(backup);
        Files.deleteIfExists(journal);
    }

    public void recoverAll() throws IOException {
        Files.createDirectories(transactionDirectory);
        try (DirectoryStream<Path> journals = Files.newDirectoryStream(transactionDirectory, "*.txn.json")) {
            for (Path journal : journals) {
                try {
                    JsonObject object = GSON.fromJson(Files.readString(journal), JsonObject.class);
                    recoverOne(journal, Path.of(object.get("target").getAsString()),
                            Path.of(object.get("pending").getAsString()), Path.of(object.get("backup").getAsString()));
                } catch (Exception error) {
                    log.error("Could not recover transaction " + journal.getFileName(), error);
                }
            }
        }
    }

    private void recoverOne(Path journal, Path target, Path pending, Path backup) throws IOException {
        if (Files.notExists(target) && Files.exists(backup)) {
            move(backup, target, true);
            log.warn("Recovered previous JAR after interrupted update: " + target.getFileName());
        } else if (Files.notExists(target) && Files.notExists(backup) && Files.exists(pending)) {
            move(pending, target, true);
            log.warn("Completed interrupted first install: " + target.getFileName());
        }
        if (Files.exists(target)) {
            Files.deleteIfExists(pending);
            Files.deleteIfExists(backup);
            Files.deleteIfExists(journal);
        }
    }

    private void writeJournal(Path path, Path target, Path pending, Path backup, String phase) throws IOException {
        JsonObject object = new JsonObject();
        object.addProperty("target", target.toAbsolutePath().normalize().toString());
        object.addProperty("pending", pending.toAbsolutePath().normalize().toString());
        object.addProperty("backup", backup.toAbsolutePath().normalize().toString());
        object.addProperty("phase", phase);
        byte[] bytes = GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        move(temporary, path, true);
    }

    private Path journal(String id) {
        return transactionDirectory.resolve(id.replaceAll("[^A-Za-z0-9_.-]", "_") + ".txn.json");
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            if (replace) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target);
        }
    }
}
