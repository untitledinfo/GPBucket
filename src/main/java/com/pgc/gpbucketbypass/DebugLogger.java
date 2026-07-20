package com.pgc.gpbucketbypass;

import java.util.logging.Logger;

/** Feature 25: verbose per-decision logging, gated by the config "debug" flag. */
public final class DebugLogger {
    private final Logger logger;
    private final ConfigManager config;
    public DebugLogger(Logger logger, ConfigManager config) { this.logger = logger; this.config = config; }
    public void log(String message) { if (config.debug()) logger.info("[GPBucket] [debug] " + message); }
}
