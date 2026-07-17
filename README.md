# GPBucketBypass

This is now a liquid-protection plugin for Paper 1.21 and GriefPrevention. It blocks lava and water bucket fills/empties for **everyone** in claims by default, including claim owners and trusted players. It never grants a GriefPrevention bypass.

## Features

- Blocks water and/or lava independently.
- Blocks filling, emptying, or both.
- `CLAIMS` scope protects every GriefPrevention claim; `EVERYWHERE` protects configured worlds including wilderness.
- Permission exemptions (`gpbucket.exempt`) and staff-managed persistent exemptions.
- Embedded SQLite database: `plugins/GPBucketBypass/data.db` stores exemptions and blocked-action audits.
- World allow-list, reload command, action messages, and configurable audit logging.

## Build and install

Run `gradle build`. Use `build/libs/GPBucketBypass-1.0.0.jar`; it includes the SQLite driver. Install beside GriefPrevention, start once, then edit `plugins/GPBucketBypass/config.yml`.

## Commands

| Command | Permission |
|---|---|
| `/gpbucket reload` | `gpbucket.reload` |
| `/gpbucket status` | `gpbucket.admin` |
| `/gpbucket exempt <player>` | `gpbucket.admin` |
| `/gpbucket unexempt <player>` | `gpbucket.admin` |

`gpbucket.exempt` defaults to `false`, so the protection applies to everyone, including server admins. Staff can grant a deliberate database exemption with the command.
