package com.pgc.gpbucketbypass;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Feature 11: checks GitHub for a newer release tag than the running version. */
public final class UpdateChecker {
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private final ConfigManager config;
    private final Logger logger;
    private final String currentVersion;
    private final AtomicReference<String> latestVersion = new AtomicReference<>(null);
    public UpdateChecker(ConfigManager config, Logger logger, String currentVersion) {
        this.config = config; this.logger = logger; this.currentVersion = currentVersion;
    }
    public void checkAsync() {
        if (!config.updateCheckerEnabled() || config.updateCheckerRepo() == null || config.updateCheckerRepo().isBlank()) return;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + config.updateCheckerRepo() + "/releases/latest"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github+json")
                .GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            Matcher matcher = TAG_PATTERN.matcher(response.body());
            if (!matcher.find()) return;
            String tag = matcher.group(1).replaceFirst("^v", "");
            if (!tag.equals(currentVersion)) {
                latestVersion.set(tag);
                logger.info("[GPBucket] A newer version is available: " + tag + " (running " + currentVersion + ")");
            }
        }).exceptionally(ex -> { logger.fine("[GPBucket] Update check failed: " + ex.getMessage()); return null; });
    }
    public String latestVersion() { return latestVersion.get(); }
}
