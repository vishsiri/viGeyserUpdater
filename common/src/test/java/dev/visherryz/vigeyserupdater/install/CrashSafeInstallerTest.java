package dev.visherryz.vigeyserupdater.install;

import dev.visherryz.vigeyserupdater.LogSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrashSafeInstallerTest {
    @TempDir Path directory;
    private static final LogSink SILENT = new LogSink() {
        public void info(String message) {} public void warn(String message) {}
        public void error(String message, Throwable error) {}
    };

    @Test void crashAfterPreparedKeepsOldJar() throws Exception {
        Scenario scenario = scenario("PREPARED", true);
        assertEquals("old", Files.readString(scenario.target));
        assertClean(scenario.target);
    }

    @Test void crashAfterBackupRestoresOldJar() throws Exception {
        Scenario scenario = scenario("BACKED_UP", true);
        assertEquals("old", Files.readString(scenario.target));
        assertClean(scenario.target);
    }

    @Test void crashAfterInstallKeepsNewJar() throws Exception {
        Scenario scenario = scenario("INSTALLED", true);
        assertEquals("new", Files.readString(scenario.target));
        assertClean(scenario.target);
    }

    @Test void interruptedFirstInstallCompletesWhenNoBackupExists() throws Exception {
        Scenario scenario = scenario("BACKED_UP", false);
        assertEquals("new", Files.readString(scenario.target));
        assertClean(scenario.target);
    }

    private Scenario scenario(String checkpoint, boolean existingTarget) throws Exception {
        Path caseDirectory = directory.resolve(checkpoint + "-" + existingTarget);
        Path transactions = caseDirectory.resolve("transactions");
        Path target = caseDirectory.resolve("plugins/example.jar");
        Path download = caseDirectory.resolve("download.jar");
        Files.createDirectories(target.getParent());
        if (existingTarget) Files.writeString(target, "old");
        Files.writeString(download, "new");
        CrashSafeInstaller crashing = new CrashSafeInstaller(transactions, SILENT, point -> {
            if (point.equals(checkpoint)) throw new SimulatedCrash();
        });
        assertThrows(SimulatedCrash.class, () -> crashing.install("example", download, target));
        new CrashSafeInstaller(transactions, SILENT).recoverAll();
        return new Scenario(target);
    }

    private static void assertClean(Path target) {
        assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".viupdater.pending")));
        assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".viupdater.bak")));
    }
    private record Scenario(Path target) {}
    private static final class SimulatedCrash extends Exception {}
}
