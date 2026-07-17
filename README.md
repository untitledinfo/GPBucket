# GPBucketBypass

Allows water and lava buckets to be used inside GriefPrevention claims,
per-liquid and per-action, controlled entirely from `config.yml`.

## How it works

GriefPrevention funnels *every* claim-permission decision through one
internal event: `ClaimPermissionCheckEvent`. Right before GriefPrevention
would deny an action, it fires this event and then checks
`getDenialReason()` — if another plugin has cleared it, the action goes
ahead as if the player had permission.

`BucketListener` listens for that event, and only touches it when:

1. `getTriggeringEvent()` is a `PlayerBucketFillEvent` or
   `PlayerBucketEmptyEvent`,
2. the player is in a world listed under `worlds:` in config.yml,
3. the player has the `gpbucket.bypass` permission, and
4. the matching config option (water/lava × fill/empty, gated by the
   `disable-*-protection` master switches) is `true`.

If all of that holds, it calls `event.setDenialReason(null)`, which is
GriefPrevention's own supported way for other plugins to override a
single permission check. Everything else a claim protects (building,
containers, PvP, etc.) is left completely untouched, and claims,
admin claims, and subdivisions are all covered automatically since they
all route through the same event.

This approach needs no NMS and doesn't race GriefPrevention's own
listener priority on the raw Bukkit bucket events.

## Building

```
./gradlew build
```

The compiled jar will be at `build/libs/GPBucketBypass-1.0.0.jar`.

Building requires network access to Maven Central and
`repo.papermc.io` (for the Paper API) — make sure your build machine
can reach those.

`libs/GriefPrevention.jar` (the jar you provided) is bundled in this
project and used as a `compileOnly` dependency so the project compiles
against the exact GriefPrevention API version you're running. It is
never shaded or shipped inside the output jar — at runtime the plugin
uses whatever GriefPrevention jar is actually on your server.

If you later update GriefPrevention, only the `ClaimPermissionCheckEvent`,
`ClaimPermission` enum, and `ClaimEvent` classes need to keep their
current shape for this plugin to keep compiling and working; these are
GriefPrevention's stable public API classes.

## Installation

1. Drop the built jar into your server's `plugins/` folder alongside
   GriefPrevention.
2. Start the server. `plugins/GPBucketBypass/config.yml` will be created.
3. Edit the config as needed, then run `/gpbucket reload`.

## Commands & permissions

| Command           | Permission        | Default | Purpose                     |
|--------------------|--------------------|---------|------------------------------|
| `/gpbucket reload` | `gpbucket.reload`  | op      | Reloads config.yml            |
| —                   | `gpbucket.bypass`  | true    | Player can use the bypass     |
