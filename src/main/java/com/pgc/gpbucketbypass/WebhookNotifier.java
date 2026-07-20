package com.pgc.gpbucketbypass;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/** Feature 8: fire-and-forget Discord webhook alerts for blocked liquid actions. */
public final class WebhookNotifier {
    private final ConfigManager config;
    private final Logger logger;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public WebhookNotifier(ConfigManager config, Logger logger) { this.config = config; this.logger = logger; }
    public void notifyBlocked(String player, String action, String world, int x, int y, int z) {
        if (!config.webhookEnabled() || config.webhookUrl() == null || config.webhookUrl().isBlank()) return;
        String content = "**GPBucket** blocked `" + action + "` by `" + player + "` at `" + world + " " + x + "," + y + "," + z + "`";
        String body = "{\"content\":\"" + escape(content) + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.webhookUrl()))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> { logger.warning("[GPBucket] Webhook delivery failed: " + ex.getMessage()); return null; });
    }
    private String escape(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\""); }
}
