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
| `authme-spigot` | JDK 21+ profile | jar (`-Spigot.jar`), Java 17 + Java 21 | One jar for Spigot 1.16–1.21.x, built in two compiler passes (see below). |
| `authme-paper` | JDK 21+ profile | jar, Java 21 | One shaded jar for Paper and Folia; the Folia adapter is chosen only on Folia. |
| `authme-velocity` | JDK 21+ profile | jar, Java 25 | Velocity proxy plugin. |

That is **8 build modules** and **5 plugin jars** — `authme-spigot-1.8`, `authme-spigot`
(`-Spigot.jar`), `authme-paper` (`-Paper.jar`), `authme-bungee` and `authme-velocity` — plus the
`authme-api` jar. `authme-core` and `authme-tools` are support jars, not plugin deliverables.

`authme-paper` is the merged Paper/Folia deliverable: its single `-Paper.jar` bundles both
`PaperPlatformAdapter` and `FoliaPlatformAdapter` in one service file, and startup selects the Folia
adapter when `RegionizedServer` is present. There is no separate Folia jar.

Server jars differ mostly in packaging and in which platform adapter they bundle; the reusable code
lives in core and the public surface in api. `authme-spigot-1.8` keeps the Java 8 floor with shaded
libraries; the modern Spigot, Paper and proxy modules are built on newer JDKs.

**Spigot merge.** `authme-spigot-legacy` and `authme-spigot-1.21` no longer exist: they were merged
into the single `authme-spigot` module, which produces one `<name>-<version>-Spigot.jar` for Spigot
1.16 through 1.21.x (needs Java 17+; a Java 21 server gets the dialog adapter). The module compiles in
two passes into one jar: `default-compile` emits Java 17 bytecode for `SpigotLegacyPlatformAdapter`,
and `compile-java21` emits Java 21 bytecode for `Spigot121PlatformAdapter`, `SpigotDialogHelper`,
`DialogApiAvailability` and `PlayerSignOpenListener`. `META-INF/services` lists
`Spigot121PlatformAdapter` first, then `SpigotLegacyPlatformAdapter`; `AuthMe.loadPlatformAdapter()`
picks the first compatible adapter and skips ones that fail to load (`UnsupportedClassVersionError`
on Java 17), so a Java 17 server silently falls back to the legacy adapter. `plugin.yml` declares
`api-version: 1.16` — a plugin cannot declare a newer api-version than the server, so this keeps the
jar loadable on the oldest supported Spigot. The former Java 17 vs Java 21 blocker is solved by this
two-pass compile.

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

The 1.8 jar keeps the Java 8 floor, and the merged Spigot jar selects its compatible adapter at
startup; no config keys, messages, permissions or the plugin id changed in the merge.

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
capability detection in `authme-paper` rather than a separate build.
