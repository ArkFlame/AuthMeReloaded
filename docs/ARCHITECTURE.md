# Architecture

AuthMeRemastered is a Maven reactor (`authme-parent`). This page records the module layout in the
tree today and the roadmap to a single server jar. The consolidation is a plan, not current code:
`authme-core` stays at Java 8 bytecode so the 1.8 jar keeps working on Spigot 1.8+, and the proxy
plugins remain separate deliverables. No configuration keys, messages, permissions, plugin id or
package names are changed by that roadmap.

## Current modules

The reactor always builds the modules below; the `java21-modules` profile adds the newer-platform
modules on JDK 21+.

| Module | Packaging / bytecode | Purpose |
| --- | --- | --- |
| `authme-api` | jar, Java 8 | Public events only; depends on `spigot-api`, `joml` and `bungeecord-chat`. |
| `authme-core` | jar, Java 8 (`core.java.version=8`) | Authentication logic, services, commands, data sources and platform abstractions. Tests run on the reactor JDK. |
| `authme-tools` | jar, Java 17 (21 on JDK 21+) | Standalone converter/utility tooling built on core. |
| `authme-spigot-1.8` | jar, Java 8 | Shaded old-server jar; carries 14 library relocations and uses the Bukkit scheduler. |
| `authme-spigot-legacy` | jar, reactor JDK | Legacy server jar; libraries are delivered via `plugin.yml`. |
| `authme-paper` | jar, Java 21 | One shaded jar for Paper and Folia; the Folia adapter is chosen only on Folia. |
| `authme-spigot-1.21` | jar, Java 21 | Shaded modern Spigot jar (brigadier/dialog support). |
| `authme-bungee` | jar, Java 21 | BungeeCord proxy plugin. |
| `authme-velocity` | jar, Java 25 | Velocity proxy plugin. |

Server jars differ mostly in packaging and in which platform adapter they bundle; the reusable code
lives in core and the public surface in api. The different compiler levels are why old and new
server jars currently ship as separate artifacts.

## Platform abstraction today

`fr.xephi.authme.platform.PlatformAdapter` is a composite of small contracts, among them
`SchedulingAdapter`, `TeleportAdapter`, `ChatAdapter`, `EventRegistrationAdapter`, `DialogAdapter`,
`CommandRegistrationAdapter`, `PacketInterceptionAdapter` and `BukkitCompatibilityAdapter`. Version
modules supply implementations, discovered through `ServiceLoader` and registered in the dependency
injection container. `AuthMe.loadPlatformAdapter()` iterates the service entries, skips ones that
fail to load, and picks the first whose `getCompatibilityError()` is null.

`SchedulingAdapter` maps three execution contexts:

- **Global** — `runOnGlobalThread`, `runDelayedOnGlobalThread`, `isGlobalThread`.
- **Entity-owned** — `runOnEntityThread`, `runDelayedOnEntityThread`,
  `runAtFixedRateOnEntityThread`, `isOwnedByCurrentThread(entity)` (and the location variant).
- **Background/async** — `runAsyncTask`, `runAsyncTaskTimer`.

Delayed and repeating methods return a `CancellableTask` handle so callers can cancel them
uniformly; the immediate global/entity methods and the thread checks are `void`/`boolean`.

Three scheduler implementations exist:

- `AbstractSpigotPlatformAdapter` (core, Java 8) maps every context onto the classic
  `BukkitScheduler` — the baseline for the 1.8 and legacy jars.
- `AbstractPaperPlatformAdapter` (`authme-paper`, Java 21) extends it and routes player-bound work
  through Paper's per-entity scheduler.
- `FoliaPlatformAdapter` (`authme-paper`, Java 21, selected only on Folia via `RegionizedServer`) implements the full region model: entity scheduler,
  `Bukkit.getGlobalRegionScheduler()`, `Bukkit.getAsyncScheduler()` and ownership checks.

This separation works, but it forces multiple jars and bytecode levels and repeats scheduling
knowledge across version modules.

## Target: one Java 8 server jar

[MODULES.md](MODULES.md) details the staged consolidation; this page describes the end state. The
proxy plugins stay separate. Platform differences become runtime capability detection instead of
build-time module selection, and scheduling sits behind the existing `SchedulingAdapter` contract as
a small provider stack:

```
SchedulingAdapter (stable API, Java 8)
        |
        v
SchedulerProvider  -- global, entity, region and async task operations
        +-- BukkitSchedulerProvider   (classic BukkitScheduler, Java 8)
        +-- PaperSchedulerProvider    (per-entity scheduler via reflection)
        +-- FoliaSchedulerProvider    (region/global/async schedulers via reflection)
```

Selection happens once at startup, following the `FoliaAPI` pattern in `../EssentialsLite`: probe for
Folia's `RegionizedServer` and scheduler accessors first, then Paper's entity scheduler, otherwise
fall back to Bukkit. Reflective method handles are cached, and no provider links Paper or Folia types
at compile time, so the jar stays Java 8 and free of hard optional dependencies. Each provider only
overrides what the platform improves: Paper refines entity-owned tasks, Folia also replaces global
and async work, and both inherit the Bukkit fallbacks.

### Folia safety rules

- Never touch an entity or world from a thread that does not own its region: check
  `isOwnedByCurrentThread` first, otherwise dispatch with `runOnEntityThread` or the region scheduler.
- Use the global region scheduler for server-wide work, the entity scheduler for player work, and the
  async scheduler only for work that touches no world state.
- Cancellable work (purge, limbo, timed teleports) must return a `CancellableTask` and tolerate a
  retired entity scheduler.
- Repeating tasks use tick-based delays/periods through the same provider, so behavior is identical
  across Bukkit, Paper and Folia.

The payoff is one server jar at one Java 8 bytecode level, scheduling tested through a single entry
point, and new platform capabilities added as another provider. Spigot 1.8 remains the guaranteed
floor, with Folia safety provided by capability detection rather than a separate build.
