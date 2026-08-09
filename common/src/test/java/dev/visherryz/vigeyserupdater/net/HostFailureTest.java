package dev.visherryz.vigeyserupdater.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostFailureTest {
    @TempDir Path directory;

    @Test void unreachableHostRetriesAndNeverTouchesExistingJar() throws Exception {
        Path existingJar = directory.resolve("existing.jar");
        Files.writeString(existingJar, "known-good");
        SecureHttpClient client = new SecureHttpClient(Set.of("127.0.0.1"), 1024 * 1024,
                1, 1, 2, 0);

        IOException failure = assertThrows(IOException.class,
                () -> client.download(URI.create("https://127.0.0.1:1/unavailable.jar"), existingJar, -1));

        assertTrue(failure.getMessage().contains("after 2 attempts"), failure.getMessage());
        assertEquals("known-good", Files.readString(existingJar));
        assertFalse(Files.exists(existingJar.resolveSibling("existing.jar.viupdater.pending")));
        assertFalse(Files.exists(existingJar.resolveSibling("existing.jar.viupdater.bak")));
    }

    @Test void disallowedHostFailsClosedBeforeCreatingDestination() {
        Path destination = directory.resolve("download.jar");
        SecureHttpClient client = new SecureHttpClient(Set.of("api.github.com"), 1024 * 1024,
                1, 1, 3, 0);

        IOException failure = assertThrows(IOException.class,
                () -> client.download(URI.create("https://example.invalid/file.jar"), destination, -1));

        assertTrue(failure.getMessage().contains("not allowlisted"), failure.getMessage());
        assertFalse(Files.exists(destination));
    }
}
