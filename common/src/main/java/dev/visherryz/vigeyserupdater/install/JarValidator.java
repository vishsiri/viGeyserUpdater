package dev.visherryz.vigeyserupdater.install;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarValidator {
    private JarValidator() {}
    public static void validate(Path path) throws IOException {
        boolean hasClass = false;
        int entries = 0;
        try (JarFile jar = new JarFile(path.toFile(), true)) {
            Enumeration<JarEntry> iterator = jar.entries();
            while (iterator.hasMoreElements()) {
                JarEntry entry = iterator.nextElement();
                entries++;
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("../") || name.contains("..\\")) {
                    throw new IOException("Unsafe JAR entry: " + name);
                }
                if (!entry.isDirectory() && name.endsWith(".class")) hasClass = true;
                if (entries > 500_000) throw new IOException("JAR contains too many entries");
            }
        }
        if (!hasClass) throw new IOException("Downloaded JAR contains no classes");
    }
}
