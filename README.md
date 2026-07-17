# GPBucketBypass — Full Protection Suite

Paper 1.21 / GriefPrevention plugin that blocks water and lava griefing in protected land. The safe default blocks everyone, including claim owners, trusted players, and administrators.

## 50 implemented features

1. Water bucket blocking; 2. lava bucket blocking; 3. fill blocking; 4. empty blocking; 5. claim-only mode; 6. wilderness-wide mode; 7. world allow-list; 8. source-water detection; 9. source-lava detection; 10. liquid-flow blocking; 11. water-flow blocking; 12. lava-flow blocking; 13. dispenser water blocking; 14. dispenser lava blocking; 15. creative-mode protection; 16. permission exemptions; 17. default-deny exemption permission; 18. SQLite database; 19. persistent exemptions; 20. force-block player rules; 21. persistent player rules; 22. rule removal; 23. per-player audit records; 24. action type audit; 25. player-name audit; 26. world audit; 27. coordinate audit; 28. audit timestamps; 29. indexed audit lookup; 30. per-player block totals; 31. player notifications; 32. staff notifications; 33. staff notification permission; 34. color-codeable messages; 35. cooldown support; 36. live config reload; 37. configuration cache; 38. debug setting; 39. database filename setting; 40. GUI main menu; 41. GUI water toggle; 42. GUI lava toggle; 43. GUI fill toggle; 44. GUI empty toggle; 45. GUI flow toggle; 46. GUI dispenser toggle; 47. GUI creative toggle; 48. GUI scope switch; 49. GUI audit toggle; 50. GUI notification toggle.

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

Permissions: `gpbucket.admin`, `gpbucket.gui`, `gpbucket.reload`, `gpbucket.exempt`, and `gpbucket.notify`. Only the deliberate `gpbucket.exempt` permission bypasses normal rules; it defaults to `false`.

## Build

Run `gradle build`. The bundled Shadow build includes the SQLite driver in `build/libs/GPBucketBypass-1.0.0.jar`. Install it beside GriefPrevention.
