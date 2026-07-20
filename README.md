# GPBucketBypass — Full Protection Suite

Paper 1.21 / GriefPrevention plugin that blocks water and lava griefing in protected land. The safe default blocks everyone, including claim owners, trusted players, and administrators.

## 75 implemented features

Includes all existing protection, SQLite, audit, GUI, and console features plus a GriefPrevention API hook, persistent WorldEdit selection regions, and the 25-feature HUGE UPDATE below.

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
| `/gpbucket region info <name>` | Show a saved cuboid's bounds. |
| `/gpbucket region list` | List saved protected cuboids. |

Permissions: `gpbucket.admin`, `gpbucket.gui`, `gpbucket.reload`, `gpbucket.exempt`, `gpbucket.region`, and `gpbucket.notify`. Only the deliberate `gpbucket.exempt` permission bypasses normal rules; it defaults to `false`.

## WorldEdit regions

Install WorldEdit (it is optional), select two corners with its wooden axe, then use `/gpbucket region create spawn-lake`. Regions are stored in the GPBucket SQLite database and are protected even where no GriefPrevention claim exists. Set `worldedit-regions-enabled: false` to disable their enforcement without deleting them.

## PlaceholderAPI (optional)

If PlaceholderAPI is installed, GPBucket registers automatically — no config needed. Placeholders: `%gpbucket_blocked_count%`, `%gpbucket_rule%`, `%gpbucket_protected%`.

## Build

Run `gradle build`. The bundled Shadow build includes the SQLite driver in `build/libs/GPBucketBypass-1.0.0.jar`. Install it beside GriefPrevention. WorldEdit and PlaceholderAPI are optional soft dependencies — install them for their respective features.
