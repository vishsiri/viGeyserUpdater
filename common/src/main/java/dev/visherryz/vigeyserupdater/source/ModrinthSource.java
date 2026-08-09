package dev.visherryz.vigeyserupdater.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.visherryz.vigeyserupdater.config.ArtifactConfig;
import dev.visherryz.vigeyserupdater.net.SecureHttpClient;

import java.net.URI;
import java.util.regex.Pattern;

public final class ModrinthSource implements ArtifactSource {
    private final SecureHttpClient http;
    public ModrinthSource(SecureHttpClient http) { this.http = http; }

    @Override
    public RemoteArtifact resolve(ArtifactConfig config) throws Exception {
        if (config.project() == null || !config.project().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid Modrinth project for " + config.id());
        }
        JsonArray versions = http.getJson(URI.create("https://api.modrinth.com/v2/project/" + config.project() + "/version")).getAsJsonArray();
        Pattern filePattern = Pattern.compile(config.assetRegex());
        for (var element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!"listed".equals(version.get("status").getAsString())
                    || !config.releaseTypes().contains(version.get("version_type").getAsString())) continue;
            JsonObject selected = null;
            for (var fileElement : version.getAsJsonArray("files")) {
                JsonObject file = fileElement.getAsJsonObject();
                if (filePattern.matcher(file.get("filename").getAsString()).matches()
                        && (selected == null || file.get("primary").getAsBoolean())) selected = file;
            }
            if (selected != null) {
                return new RemoteArtifact(version.get("id").getAsString(), selected.get("filename").getAsString(),
                        URI.create(selected.get("url").getAsString()), selected.get("size").getAsLong(), null);
            }
        }
        throw new IllegalStateException("No eligible Modrinth version/file found for " + config.id());
    }
}
