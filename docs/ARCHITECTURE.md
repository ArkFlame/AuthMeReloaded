# Architecture

AuthMeRemastered is a Maven reactor (`authme-parent`) with **eight modules**: four built by default
and four added by the `java21-modules` profile. This page records the module layout in the tree
today. The Spigot 1.16–1.21.x lines are now merged into a single `authme-spigot` module that builds
one jar; `authme-core` stays at Java 8 bytecode so the 1.8 jar keeps working on Spigot 1.8+, and the
proxy plugins remain separate deliverables. No configuration keys, messages, permissions, plugin id or
package names changed during that merge.

## Current modules

The default reactor builds the modules marked **always** below; the `java21-modules` profile adds the
modules marked **JDK 21+ profile** on JDK 21+ (Velocity itself needs JDK 25+ to compile).

| Module | Reactor | Packaging / bytecode | Purpose |
| --- | --- | --- | --- |
| `authme-api` | always | jar, Java 8 | Public events only; depends on `spigot-api`, `joml` and `bungeecord-chat`. |
| `authme-core` | always | jar, Java 8 (`core.java.version=8`) | Authentication logic, services, commands, data sources and platform abstractions. Tests run on the reactor JDK. |
| `authme-tools` | always | jar, Java 17 (21 on JDK 21+) | Standalone converter/utility tooling built on core. |
| `authme-spigot-1.8` | always | jar, Java 8 | Shaded jar for Spigot 1.8.8; carries 14 library relocations and uses the Bukkit scheduler. |
| `authme-bungee` | JDK 21+ profile | jar, Java 21 | BungeeCord proxy plugin. |
| `authme-spigot` | JDK 21+ profile | jar (`-Spigot.jar`), Java 17 + Java 21 | One jar for Spigot 1.16–1.21.x, Paper and Folia; the adapter is chosen at startup (see below). |
| `authme-velocity` | JDK 21+ profile | jar, Java 25 | Velocity proxy plugin. |

That is **7 build modules** and **4 plugin jars** — `authme-spigot-1.8`, `authme-spigot`
(`-Spigot.jar`), `authme-bungee` and `authme-velocity` — plus the
`authme-api` jar. `authme-core` and `authme-tools` are support jars, not plugin deliverables.

`authme-spigot` is also the Paper/Folia deliverable: its single `-Spigot.jar` bundles the Folia, Paper and Spigot
adapters in one service file (Folia, Paper, Spigot 1.21, Spigot legacy, in that order); startup picks the first one whose required classes exist, so Folia wins on Folia
and Paper wins on Paper. There is no separate Paper or Folia jar.

Server jars differ mostly in packaging and in which platform adapter they bundle; the reusable code
lives in core and the public surface in api. `authme-spigot-1.8` keeps the Java 8 floor with shaded
libraries; the modern Spigot, Paper and proxy modules are built on newer JDKs.

**Spigot merge.** The former `authme-spigot-legacy` and `authme-spigot-1.21` modules are now one
`authme-spigot` jar for 1.16–1.21.x (Java 17+; Java 21 adds dialogs). Its Java 17/Java 21 two-pass
compile and runtime adapter selection are detailed in [MODULES.md](MODULES.md) and [build.md](build.md);
`plugin.yml` declares `api-version: 1.16` to stay loadable on the oldest supported Spigot.

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

`SchedulerProvider` (core, Java 8) is the shared runtime layer for global and async work.
`SchedulerProviders.current()` detects the server once at startup by probing for Folia's
`GlobalRegionScheduler`: Folia gets `FoliaSchedulerProvider` (all Folia calls are reflective, so core
stays Java 8 and Spigot 1.8 safe), every other server gets `BukkitSchedulerProvider`.
`AbstractSpigotPlatformAdapter` delegates global/async work to that provider and maps entity work onto
the global thread; `FoliaPlatformAdapter` uses it for global/async work and `FoliaTaskScheduler` for
entity-owned tasks. The 1.8 jar reaches the same core provider through its Spigot adapter.

## Folia safety rules

- Never touch an entity or world from a thread that does not own its region: check
  `isOwnedByCurrentThread` first, otherwise dispatch with `runOnEntityThread` or the region scheduler.
- Use the global region scheduler for server-wide work, the entity scheduler for player work, and the
  async scheduler only for work that touches no world state.
- Cancellable work (purge, limbo, timed teleports) must return a `CancellableTask` and tolerate a
  retired entity scheduler.
- Repeating tasks use tick-based delays/periods through the same provider, so behavior is identical
  across Bukkit, Paper and Folia.

Spigot 1.8 remains the guaranteed floor via `authme-spigot-1.8`, with Folia safety provided by
capability detection in `authme-spigot` rather than a separate build.
