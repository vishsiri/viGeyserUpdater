package dev.visherryz.vigeyserupdater.source;

import com.google.gson.JsonObject;
import dev.visherryz.vigeyserupdater.config.ArtifactConfig;
import dev.visherryz.vigeyserupdater.net.SecureHttpClient;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

public final class GeyserDownloadSource implements ArtifactSource {
    private final SecureHttpClient http;

    public GeyserDownloadSource(SecureHttpClient http) {
        this.http = http;
    }

    @Override
    public RemoteArtifact resolve(ArtifactConfig config) throws Exception {
        if (config.project() == null || !config.project().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid Geyser project for " + config.id());
        }
        String base = "https://download.geysermc.org/v2/projects/" + config.project()
                + "/versions/latest/builds/latest";
        JsonObject build = http.getJson(URI.create(base)).getAsJsonObject();
        JsonObject downloads = build.getAsJsonObject("downloads");
        Pattern pattern = Pattern.compile(config.assetRegex());
        Map.Entry<String, com.google.gson.JsonElement> selected = null;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : downloads.entrySet()) {
            JsonObject download = entry.getValue().getAsJsonObject();
            if (pattern.matcher(download.get("name").getAsString()).matches()) {
                if (selected != null) throw new IllegalStateException("Multiple downloads match " + config.assetRegex());
                selected = entry;
            }
        }
        if (selected == null) throw new IllegalStateException("No Geyser download matches " + config.assetRegex());
        JsonObject download = selected.getValue().getAsJsonObject();
        String identity = build.get("version").getAsString() + ":" + build.get("build").getAsInt()
                + ":" + selected.getKey();
        return new RemoteArtifact(identity, download.get("name").getAsString(),
                URI.create(base + "/downloads/" + selected.getKey()), -1,
                download.get("sha256").getAsString());
    }
}
