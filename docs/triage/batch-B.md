# Batch B triage

| Issue | Title | Classification | Notes |
| --- | --- | --- | --- |
| 3115 | UUID command | feature, implemented | New `/authme uuid <name>` admin command reports the offline UUID, the stored Mojang UUID when premium data exists, and the Floodgate UUID for an online Bedrock player; no network lookups are performed. |
| 3152 | Permission-Based Tab Completion for AuthMe Subcommands | feature, deferred | Paper-only: filter Brigadier child suggestions by the child command's `PermissionNode` before sending them, so unauthorized subcommands are hidden from tab completion.<br>Keep the legacy Bukkit completion path unchanged and revisit once the Paper registrar is shared across all builds. |
