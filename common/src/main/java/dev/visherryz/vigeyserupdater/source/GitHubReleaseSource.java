package dev.visherryz.vigeyserupdater.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.visherryz.vigeyserupdater.config.ArtifactConfig;
import dev.visherryz.vigeyserupdater.net.SecureHttpClient;

import java.net.URI;
import java.util.regex.Pattern;

public final class GitHubReleaseSource implements ArtifactSource {
    private final SecureHttpClient http;
    public GitHubReleaseSource(SecureHttpClient http) { this.http = http; }

    @Override
    public RemoteArtifact resolve(ArtifactConfig config) throws Exception {
        if (config.repository() == null || !config.repository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid GitHub repository for " + config.id());
        }
        URI endpoint = URI.create("https://api.github.com/repos/" + config.repository() + "/releases/latest");
        JsonObject release = http.getJson(endpoint).getAsJsonObject();
        Pattern pattern = Pattern.compile(config.assetRegex());
        JsonArray assets = release.getAsJsonArray("assets");
        JsonObject selected = null;
        for (var element : assets) {
            JsonObject asset = element.getAsJsonObject();
            if (pattern.matcher(asset.get("name").getAsString()).matches()) {
                if (selected != null) throw new IllegalStateException("Multiple assets match " + config.assetRegex());
                selected = asset;
            }
        }
        if (selected == null) throw new IllegalStateException("No release asset matches " + config.assetRegex());
        String digest = selected.has("digest") && !selected.get("digest").isJsonNull() ? selected.get("digest").getAsString() : null;
        String sha256 = digest != null && digest.startsWith("sha256:") ? digest.substring(7) : null;
        String identity = release.get("tag_name").getAsString() + ":" + selected.get("id").getAsLong()
                + ":" + selected.get("updated_at").getAsString();
        return new RemoteArtifact(identity, selected.get("name").getAsString(),
                URI.create(selected.get("browser_download_url").getAsString()), selected.get("size").getAsLong(), sha256);
    }
}
