package com.pgc.gpbucketbypass;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/** Feature 8 (plain alerts) + Feature 92 (rich embeds): fire-and-forget Discord webhook alerts for blocked liquid actions. */
public final class WebhookNotifier {
    private final ConfigManager config;
    private final Logger logger;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public WebhookNotifier(ConfigManager config, Logger logger) { this.config = config; this.logger = logger; }
    public void notifyBlocked(String player, String action, String world, int x, int y, int z) {
        if (!config.webhookEnabled() || config.webhookUrl() == null || config.webhookUrl().isBlank()) return;
        // Feature 92: a proper embed (title/fields/color/timestamp) reads far better in Discord than a single content line.
        String color = safeColor(config.webhookEmbedColor());
        String iso = java.time.Instant.now().toString();
        String body = "{\"embeds\":[{"
                + "\"title\":\"GPBucket — blocked liquid action\","
                + "\"color\":" + color + ","
                + "\"timestamp\":\"" + iso + "\","
                + "\"fields\":["
                + "{\"name\":\"Player\",\"value\":\"" + escape(player) + "\",\"inline\":true},"
                + "{\"name\":\"Action\",\"value\":\"" + escape(action) + "\",\"inline\":true},"
                + "{\"name\":\"Location\",\"value\":\"" + escape(world + " " + x + ", " + y + ", " + z) + "\",\"inline\":false}"
                + "]}]}";
        send(body);
    }
    /** Feature 111: daily summary embed, sent once every 24h alongside the console digest. */
    public void notifyDigest(long blockedInLastDay) {
        if (config.webhookUrl() == null || config.webhookUrl().isBlank()) return;
        String body = "{\"embeds\":[{"
                + "\"title\":\"GPBucket — daily digest\","
                + "\"color\":" + safeColor(config.webhookEmbedColor()) + ","
                + "\"timestamp\":\"" + java.time.Instant.now() + "\","
                + "\"description\":\"" + blockedInLastDay + " liquid-griefing attempt(s) blocked in the last 24 hours.\""
                + "}]}";
        send(body);
    }
    private void send(String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.webhookUrl()))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> { logger.warning("[GPBucket] Webhook delivery failed: " + ex.getMessage()); return null; });
    }
    private String safeColor(String configured) { try { return String.valueOf(Integer.parseInt(configured.trim())); } catch (Exception e) { return "15158332"; } }
    private String escape(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\""); }
}
