# GPBucketBypass — Full Protection Suite

Paper 1.21 / GriefPrevention plugin that blocks water and lava griefing in protected land. The safe default blocks everyone, including claim owners, trusted players, and administrators.

## 50 implemented features

Includes all existing protection, SQLite, audit, GUI, and console features plus a GriefPrevention API hook and persistent WorldEdit selection regions.

## Commands

| Command | Purpose |
|---|---|
| `/gpbucket gui` | Open the live admin GUI. |
| `/gpbucket reload` | Reload configuration. |
| `/gpbucket status` | Display current protection state. |
| `/gpbucket exempt <player>` | Persistently allow a player. |
| `/gpbucket unexempt <player>` | Remove a player rule. |
| `/gpbucket block <player>` | Force-block a player, overriding exemptions. |
| `/gpbucket unblock <player>` | Remove the force-block rule. |
| `/gpbucket stats <player>` | Show blocked-action total and player rule. |
| `/gpbucket region create <name>` | Save the current WorldEdit wooden-axe selection as a protected cuboid. |
| `/gpbucket region delete <name>` | Delete a saved protected cuboid. |
| `/gpbucket region list` | List saved protected cuboids. |

Permissions: `gpbucket.admin`, `gpbucket.gui`, `gpbucket.reload`, `gpbucket.exempt`, and `gpbucket.notify`. Only the deliberate `gpbucket.exempt` permission bypasses normal rules; it defaults to `false`.

## WorldEdit regions

Install WorldEdit (it is optional), select two corners with its wooden axe, then use `/gpbucket region create spawn-lake`. Regions are stored in the GPBucket SQLite database and are protected even where no GriefPrevention claim exists. Set `worldedit-regions-enabled: false` to disable their enforcement without deleting them.

## Build

Run `gradle build`. The bundled Shadow build includes the SQLite driver in `build/libs/GPBucketBypass-1.0.0.jar`. Install it beside GriefPrevention.
