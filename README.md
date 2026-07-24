# GPBucketBypass — Full Protection Suite

Paper 1.21 / GriefPrevention plugin that blocks water and lava griefing in protected land. The safe default blocks everyone, including claim owners, trusted players, and administrators.

## 120 implemented features

Includes all existing protection, SQLite, audit, GUI, and console features plus a GriefPrevention API hook, persistent WorldEdit selection regions, the 25-feature HUGE UPDATE, the 25-feature ADVANCED UPDATE, and the 20-feature NEXT UPDATE below.

## HUGE UPDATE — 25 new features

1. **Fire spread protection** — fire can no longer spread onto protected land.
2. **Flint & steel protection** — players can't light fires on protected land.
3. **Cauldron protection** — cauldrons can't be filled/emptied with buckets on protected land.
4. **Powder snow bucket protection** — treated the same as water/lava.
5. **Combat-tag lockout** — recently-PvP-tagged players can't use liquid buckets at all (`combat-tag-seconds`).
6. **Per-world scope overrides** — force `EVERYWHERE` or `CLAIMS` per world (`worlds-scope-override`).
7. **Chat/action-bar spam cooldown** — stops repeated blocked-message spam (`message-cooldown-ms`).
8. **Discord webhook alerts** — posts every blocked action to a webhook (`webhook.*`).
9. **PlaceholderAPI support** — `%gpbucket_blocked_count%`, `%gpbucket_rule%`, `%gpbucket_protected%` (auto-registers if PlaceholderAPI is installed).
10. **Auto-block escalation** — repeat offenders get temporarily force-blocked (`auto-block.*`).
11. **GitHub update checker** — checks for newer releases on startup, notifies staff on join.
12. **Audit log auto-retention** — purges old audit rows on a schedule (`audit-retention-days`).
13. **`/gpbucket top [limit]`** — leaderboard of the most-blocked players.
14. **`/gpbucket history [page]`** — global paginated blocked-action log.
15. **`/gpbucket region rename <old> <new>`** — rename a saved region.
16. **Region-delete confirmation** — `/gpbucket region delete` must be run twice within 10s.
17. **Region-create rate limit** — per-player cooldown on region creation (`region-create-cooldown-ms`).
18. **Join tutorial message** — first-time reminder when a player stands on protected land.
19. **Tab-completion** — online player names autocomplete for admin subcommands.
20. **GUI page 2** — a second admin-GUI page with toggles for all of the above protections.
21. **Distinct exempt sound** — separate confirmation sound for allowed (not blocked) liquid use.
22. **Config auto-backup & migration** — old `config.yml` is backed up automatically on upgrade.
23. **`/gpbucket export`** — dumps the full audit log to a CSV file in the plugin folder.
24. **Blocked-rate stat in `/gpbucket status`** — shows blocked actions in the last hour plus the running total.
25. **Verbose debug logging** — the previously-unused `debug: true` flag now logs every allow/block decision to console.

## ADVANCED UPDATE — 25 more new features

76. **Region priority** — overlapping protected regions resolve by priority (`/gpbucket region priority <name> <n>`); highest wins.
77. **Per-region liquid flag overrides** — allow or deny a specific liquid in one region regardless of global scope (`/gpbucket region flag <name> <water|lava|powder_snow> <allow|deny|default>`).
78. **Timed player rules** — `/gpbucket exempt|block <player> [minutes]` for auto-expiring rules, swept clean in the background.
79. **`/gpbucket whois <player>`** — unified dashboard: rule + expiry, blocked count, risk score, auto-block state, recent history.
80. **`/gpbucket panic`** — instant global lockdown; blocks every liquid bucket everywhere until toggled off (runtime-only, resets on restart).
81. **`/gpbucket simulate`** — per-admin dry-run mode: your actions still succeed, but you see exactly what would have been blocked.
82. **Protection schedule** — restrict protection to a configured time-of-day window (`schedule.*`).
83. **Claim-owner grief notifications** — the claim owner is told (if online) when someone is blocked in their claim.
84. **Milestone broadcasts** — server-wide announcement when total blocked actions cross a configured threshold (`milestones`).
85. **Public developer API** — `GPBucketAPI` + `BucketBlockedEvent` for other plugins to hook into.
86. **Locale support** — drop a `messages_<locale>.yml` in the data folder and set `locale:` to override any message.
87. **`/gpbucket audit search <keyword>`** — search the global audit log by player, action, or world.
88. **`/gpbucket forgive <player>`** — clears a player's active auto-block and resets their offense window.
89. **Region export/import** — `/gpbucket region export [file]` / `import <file>`, a full YAML snapshot including flags and priority.
90. **`/gpbucket region show <name>`** — outlines a region with particles, visible only to you, for 8 seconds.
91. **One-time staff bypass codes** — `/gpbucket bypasscode generate` / `redeem <code>`.
92. **Rich Discord embeds** — webhook alerts (feature 8) now send a proper embed instead of plain text (`webhook.embed-color`).
93. **`/gpbucket cooldown <player>`** — clears a player's active bucket-use cooldown.
94. **Background rule-expiry sweep** — timed rules (feature 78) are cleared automatically every 5 minutes.
95. **Severity-weighted risk score** — lava griefing weighs more than water/snow, surfaced in `/gpbucket whois`.
96. **`/gpbucket version`** — verbose diagnostics: uptime, database size, region/world counts, panic/schedule state, dependency status.
97. **`/gpbucket config get|set <key> [value]`** — live single-key config editor without a full reload.
98. **Join-tutorial world exclusions** — skip the first-join reminder in listed worlds (`join-tutorial-exclude-worlds`).
99. **Local metrics summary** — hourly health/usage summary logged to console; nothing leaves the server (`metrics.enabled`).
100. **Startup self-diagnostics** — warns on enable if GriefPrevention/WorldEdit are missing, the data folder isn't writable, or the webhook is misconfigured.

## NEXT UPDATE — 20 more new features

101. **Rule reasons** — `/gpbucket exempt|block <player> [minutes] [reason...]` now stores a free-text reason, shown in `whois`.
102. **`/gpbucket note <player> <text>`** — staff notes, shown in whois.
103. **`/gpbucket warn <player> <reason>`** — escalating warnings; a player is auto-blocked at `warn-threshold`.
104. **Region tags** — `/gpbucket region tag <name> <tags>` / `taglist <tag>`, for grouping regions.
105. **`/gpbucket top risk [n]`** — leaderboard sorted by risk score instead of raw blocked count.
106. **`/gpbucket backup`** — snapshots the SQLite database to a timestamped file.
107. **`/gpbucket region merge <a> <b> <newname>`** — bounding-box union of two regions, replacing the originals.
108. **New-player grace period** — auto-block escalation is suspended for `grace-period-minutes` after joining.
109. **Throttled staff alerts** — rapid repeats collapse into a "(+N more since last alert)" summary (`staff-alert-cooldown-ms`).
110. **`/gpbucket testflag <player> <water|lava|powder_snow>`** — dry-run protection check, no config needed.
111. **Daily digest** — a 24h summary of blocked activity, to console and Discord.
112. **`/gpbucket ignore`** — personal opt-out from live blocked-action broadcasts.
113. **Claim-owner notify cooldown** — avoids repeat-spamming an owner for the same offender (`claim-owner-notify-cooldown-ms`).
114. **Max regions per player** — caps how many WorldEdit regions one player may own (`max-regions-per-player`).
115. **`/gpbucket region list [page]`** — paginated at 15 per page.
116. **`/gpbucket audit clear`** — confirmed wipe of the entire audit log.
117. **Proactive protected-area warning** — an action-bar warning the moment a player holding a bucket crosses into protected land.
118. **`/gpbucket rules`** — in-game command reference, tailored to the sender's permissions.
119. **Config validation on load** — nonsensical values now log a clear console warning instead of misbehaving silently.
120. **`/gpbucket audit mine`** — any player can check their own blocked-action history, no admin permission required.

## Commands

| Command | Purpose |
|---|---|
| `/gpbucket gui` | Open the live admin GUI (now two pages). |
| `/gpbucket reload` | Reload configuration. |
| `/gpbucket status` | Display current protection state and blocked-action rate. |
| `/gpbucket report` | Print the console protection summary. |
| `/gpbucket inspect` | Show whether your current location is protected and why. |
| `/gpbucket exempt <player>` | Persistently allow a player. |
| `/gpbucket unexempt <player>` | Remove a player rule. |
| `/gpbucket block <player>` | Force-block a player, overriding exemptions. |
| `/gpbucket unblock <player>` | Remove the force-block rule. |
| `/gpbucket stats <player>` | Show blocked-action total and player rule. |
| `/gpbucket audit <player>` | Show a player's 5 most recent blocked actions. |
| `/gpbucket top [limit]` | Leaderboard of the most-blocked players. |
| `/gpbucket history [page]` | Global paginated blocked-action log. |
| `/gpbucket export` | Export the full audit log to CSV. |
| `/gpbucket region create <name>` | Save the current WorldEdit wooden-axe selection as a protected cuboid. |
| `/gpbucket region delete <name>` | Delete a saved protected cuboid (repeat within 10s to confirm). |
| `/gpbucket region rename <old> <new>` | Rename a saved protected cuboid. |
| `/gpbucket region info <name>` | Show a saved cuboid's bounds, priority, and flags. |
| `/gpbucket region list` | List saved protected cuboids. |
| `/gpbucket region flag <name> <water\|lava\|powder_snow> <allow\|deny\|default>` | Set a per-region liquid override. |
| `/gpbucket region priority <name> <n>` | Set a region's overlap-resolution priority. |
| `/gpbucket region export [file]` / `import <file>` | YAML snapshot of all regions (with flags/priority). |
| `/gpbucket region show <name>` | Outline a region with particles for 8 seconds. |
| `/gpbucket whois <player>` | Unified rule/blocked-count/risk-score/history dashboard. |
| `/gpbucket panic` | Toggle instant global lockdown. |
| `/gpbucket simulate` | Toggle your own dry-run testing mode. |
| `/gpbucket forgive <player>` | Clear a player's auto-block state. |
| `/gpbucket cooldown <player>` | Clear a player's bucket-use cooldown. |
| `/gpbucket bypasscode generate` / `redeem <code>` | One-time staff bypass codes. |
| `/gpbucket audit search <keyword>` | Search the global audit log. |
| `/gpbucket config get\|set <key> [value]` | Live single-key config editor. |
| `/gpbucket version` | Verbose diagnostics. |
| `/gpbucket note <player> <text>` | Add a staff note (shown in whois). |
| `/gpbucket warn <player> <reason>` | Issue a warning; auto-blocks at the threshold. |
| `/gpbucket backup` | Snapshot the SQLite database. |
| `/gpbucket region merge <a> <b> <newname>` | Union two regions into a new one. |
| `/gpbucket region tag <name> <tags>` / `taglist <tag>` | Group regions by tag. |
| `/gpbucket top risk [n]` | Risk-score leaderboard. |
| `/gpbucket testflag <player> <water\|lava\|powder_snow>` | Dry-run protection check. |
| `/gpbucket ignore` | Personal opt-out from live blocked-action broadcasts. |
| `/gpbucket audit clear` / `mine` | Wipe the audit log / check your own history. |
| `/gpbucket rules` | In-game command reference. |

Permissions: `gpbucket.admin`, `gpbucket.gui`, `gpbucket.reload`, `gpbucket.exempt`, `gpbucket.region`, and `gpbucket.notify`. Only the deliberate `gpbucket.exempt` permission bypasses normal rules; it defaults to `false`. All ADVANCED UPDATE and NEXT UPDATE admin commands reuse `gpbucket.admin`; `/gpbucket config` reuses `gpbucket.reload`. `/gpbucket audit mine` and `/gpbucket inspect` (self-checks) need no special permission beyond what's already listed.

## WorldEdit regions

Install WorldEdit (it is optional), select two corners with its wooden axe, then use `/gpbucket region create spawn-lake`. Regions are stored in the GPBucket SQLite database and are protected even where no GriefPrevention claim exists. Set `worldedit-regions-enabled: false` to disable their enforcement without deleting them. Regions also support a numeric `priority` (feature 76) for overlap resolution and per-liquid `flag` overrides (feature 77).

## Developer API

Other plugins can add `compileOnly(files("path/to/GPBucketBypass.jar"))` and depend on `GPBucketBypass` in their `plugin.yml`, then call `GPBucketAPI.isProtected(location)`, `GPBucketAPI.isDatabaseExempt(player)`, `GPBucketAPI.blockedCount(player)`, or `GPBucketAPI.isPanicMode()`. Listen for `BucketBlockedEvent` to react whenever GPBucket blocks a liquid action.

## PlaceholderAPI (optional)

If PlaceholderAPI is installed, GPBucket registers automatically — no config needed. Placeholders: `%gpbucket_blocked_count%`, `%gpbucket_rule%`, `%gpbucket_protected%`, `%gpbucket_risk_score%`, `%gpbucket_panic%`.

## Build

Run `gradle build`. The bundled Shadow build includes the SQLite driver in `build/libs/GPBucketBypass-3.0.0.jar`. Install it beside GriefPrevention. WorldEdit and PlaceholderAPI are optional soft dependencies — install them for their respective features.
