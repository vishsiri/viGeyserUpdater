package dev.visherryz.vigeyserupdater.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JarValidatorTest {
    @TempDir Path directory;
    @Test void acceptsJarWithClass() throws Exception {
        Path jar = jar("ok.jar", "example/Main.class");
        assertDoesNotThrow(() -> JarValidator.validate(jar));
    }
    @Test void rejectsArchiveWithoutClasses() throws Exception {
        Path jar = jar("empty.jar", "config.yml");
        assertThrows(IOException.class, () -> JarValidator.validate(jar));
    }
    @Test void rejectsNonJar() throws Exception {
        Path file = directory.resolve("bad.jar"); Files.writeString(file, "not a jar");
        assertThrows(IOException.class, () -> JarValidator.validate(file));
    }
    private Path jar(String name, String entryName) throws Exception {
        Path path = directory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(entryName)); output.write(new byte[] {1, 2, 3}); output.closeEntry();
        }
        return path;
    }
}
