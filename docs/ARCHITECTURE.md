# Architecture

This page describes the current module layout of AuthMeRemastered and the target single-module
design, including the layered `SchedulerProvider` model used to keep one jar safe on Spigot 1.8+,
Paper and Folia. It is a design document: no configuration keys, messages, permissions, plugin id
or package names are changed by it.

## Current modules

The build is a Maven reactor (`authme-parent`) with a small set of always-built modules and a
`java21-modules` profile that adds the newer-platform modules on JDK 21+.

| Module | Packaging / bytecode | Purpose |
| --- | --- | --- |
| `authme-api` | jar, Java 8 | Public API contracts; depends only on `spigot-api`, `joml` and `bungeecord-chat`. |
| `authme-core` | jar, Java 8 (`core.java.version=8`) | All authentication logic, services, commands, data sources and the platform abstraction layer. Tests run on the reactor JDK. Core must stay Java 8 for the 1.8 deliverable. |
| `authme-tools` | jar, reactor JDK | Standalone converter/utility tooling built on top of core. |
| `authme-spigot-1.8` | jar, Java 8 | Shaded server jar for old Spigot/CraftBukkit; uses the Bukkit scheduler. |
| `authme-spigot-legacy` | jar, reactor JDK | Legacy server jar variant with its own shading/antrun packaging. |
| `authme-paper-common` | jar, Java 21 | Shared Paper code, notably `AbstractPaperPlatformAdapter` and dialog helpers. |
| `authme-paper` | jar, Java 21 | Shaded Paper server jar. |
| `authme-folia` | jar, Java 21 | Shaded Folia server jar; region-aware platform adapter on `folia-api`. |
| `authme-spigot-1.21` | jar, Java 21 | Shaded modern Spigot server jar (brigadier/dialog support). |
| `authme-bungee` | jar, Java 21 | Proxy-side plugin for BungeeCord. |
| `authme-velocity` | jar, Java 25 | Proxy-side plugin for Velocity. |

The server jars differ mostly in packaging and in which platform adapter they bundle; the reusable
logic lives in `authme-core` and the public surface in `authme-api`. The different compiler levels
(8, 17, 21, 25) are the reason the old-server and new-server jars currently ship as separate
artifacts.

## Platform abstraction today

`fr.xephi.authme.platform.PlatformAdapter` is a composite interface extending several small
contracts, among them `SchedulingAdapter`, `TeleportAdapter`, `ChatAdapter`,
`EventRegistrationAdapter`, `DialogAdapter`, `CommandRegistrationAdapter`,
`PacketInterceptionAdapter` and `BukkitCompatibilityAdapter`. Version modules provide the
concrete implementations, discovered via `ServiceLoader` and registered into the dependency
injection container.

`SchedulingAdapter` is the bridge between AuthMe's task concepts and the server's scheduler. It
distinguishes three execution contexts:

- **Global** work (`runOnGlobalThread`, `runDelayedOnGlobalThread`, `isGlobalThread`).
- **Entity-owned** work (`runOnEntityThread`, `runDelayedOnEntityThread`,
  `runAtFixedRateOnEntityThread`, `isOwnedByCurrentThread(entity)`).
- **Background/async** work (`runAsyncTask`, `runAsyncTaskTimer`).

All methods return or accept handles that are wrapped as `CancellableTask` so callers can cancel
them uniformly.

Three scheduler implementations exist today:

- `AbstractSpigotPlatformAdapter` (core, Java 8) maps every context onto the classic
  `BukkitScheduler` (`runTask`, `runTaskLater`, `runTaskTimer*`, `runTaskAsynchronously`). This is
  the baseline used by the Spigot 1.8 and legacy jars.
- `AbstractPaperPlatformAdapter` (paper-common, Java 21) extends the Spigot adapter and routes
  player-bound work through Paper's per-entity scheduler (`player.getScheduler()`).
- `FoliaPlatformAdapter` (folia, Java 21) implements the full region model: entity-owned tasks via
  `entity.getScheduler()`, global work via `Bukkit.getGlobalRegionScheduler()`, and background work
  via `Bukkit.getAsyncScheduler()`. It also checks that the calling thread owns the entity or
  region before touching it.

This separation works, but it forces multiple jars and multiple bytecode levels, and it duplicates
the scheduling knowledge across version modules.

## Reference: the EssentialsLite approach

`../EssentialsLite` is the reference for the target shape. It is a **single Maven jar** with
`maven.compiler.release=8`, built against `paper-api`, and it handles Spigot, Paper and Folia from
one codebase. Its `utils.FoliaAPI` is the key pattern:

- Folia is detected once at class-init time by probing for
  `io.papermc.paper.threadedregions.RegionizedServer` and the scheduler accessors, never with a
  compile-time Folia dependency.
- Scheduler methods (`getGlobalRegionScheduler`, `getRegionScheduler`, `getAsyncScheduler`,
  `Entity#getScheduler`, `teleportAsync`, region ownership checks) are resolved reflectively and
  cached in a method map.
- Each public operation (`runTask`, `runTaskAsync`, `runTaskTimer`, entity/region variants, tracked
  repeating tasks) picks the Folia path when available and otherwise falls back to the Bukkit
  scheduler. Tasks are wrapped in a tracked handle with a reflectively resolved `cancel()`.

The result is one Java 8 artifact that remains Folia-safe without requiring new bytecode or
separate per-server builds.

## Target: one module with layered `SchedulerProvider`s

The direction is to collapse the server-side modules into a **single Java 8 module/jar** (the proxy
plugins remain separate deliverables because they target BungeeCord/Velocity, not Bukkit). Core
stays at Java 8 bytecode so the jar keeps working on Spigot 1.8+. Platform differences become
runtime capability detection rather than build-time module selection.

Scheduling is expressed as a small stack of providers behind the existing `SchedulingAdapter`
contract, so callers (notably `BukkitService`) keep using global / entity-owned / async operations
unchanged:

```
SchedulingAdapter (stable API, Java 8)
        |
        v
SchedulerProvider  -- interface: global, entity, region and async task operations
        |
        +-- BukkitSchedulerProvider   (base; classic BukkitScheduler, Java 8)
        +-- PaperSchedulerProvider    (adds per-entity scheduler via reflection)
        +-- FoliaSchedulerProvider    (region/global/async schedulers via reflection)
```

Provider selection happens once at startup, mirroring `FoliaAPI`:

1. Probe for Folia's `RegionizedServer` and scheduler classes. If present, use
   `FoliaSchedulerProvider`; cache the reflective method handles for the global, region, entity and
   async schedulers and for `isOwnedByCurrentRegion`.
2. Otherwise, probe for Paper's entity scheduler API. If present, use
   `PaperSchedulerProvider` for entity-owned work and inherit the Bukkit global/async paths.
3. Otherwise use `BukkitSchedulerProvider`, which maps all three contexts to the classic
   `BukkitScheduler`.

Layering means each provider only overrides what the platform improves: Paper refines entity-owned
tasks, Folia additionally replaces global and async tasks, and both fall back to the Bukkit
implementation for anything the platform does not expose. No provider is bound at compile time to
Paper or Folia types — capability checks and cached reflection keep the jar at Java 8 and free of
hard optional dependencies.

### Folia safety rules

- Never touch an entity or world from a thread that does not own its region; use
  `isOwnedByCurrentThread` before acting and `runOnEntityThread` / region-scheduler dispatch
  otherwise.
- Use the global region scheduler for server-wide work, the entity scheduler for player-specific
  work, and the async scheduler for background work that touches no world state.
- Tasks that may be cancelled (purge, limbo, timed teleports) must return a `CancellableTask` and
  tolerate a retired entity scheduler, matching the current `FoliaPlatformAdapter` behavior.
- Repeating tasks use tick-based delays/periods through the same provider so behavior is identical
  across Bukkit, Paper and Folia.

### Benefits

- One jar and one Java 8 bytecode level instead of parallel 8/21/25 server artifacts.
- Scheduler behavior tested in one place through a single `SchedulingAdapter` entry point.
- New platform capabilities can be added as another provider without changing callers or the API.
- Spigot 1.8 remain the guaranteed floor, and Folia safety is preserved by capability detection
  rather than by shipping a separate Folia-only build.
