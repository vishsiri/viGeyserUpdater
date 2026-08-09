package dev.visherryz.vigeyserupdater.state;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public final class StateStore {
    private static final Gson GSON = new Gson();
    private final Path path;
    private final Map<String, InstalledArtifact> state;

    public StateStore(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            Map<String, InstalledArtifact> loaded = GSON.fromJson(Files.readString(path),
                    new TypeToken<Map<String, InstalledArtifact>>() {}.getType());
            this.state = loaded == null ? new HashMap<>() : new HashMap<>(loaded);
        } else this.state = new HashMap<>();
    }
    public synchronized InstalledArtifact get(String id) { return state.get(id); }
    public synchronized void put(String id, InstalledArtifact value) throws IOException {
        state.put(id, value);
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        byte[] bytes = GSON.toJson(state).getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes)); channel.force(true);
        }
        try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
