# Modules

AuthMeReloaded is built as eight Maven modules. Five of them are plugin jars —
`Spigot-1.8`, `Spigot`, `Paper`, `Bungee` and `Velocity` — and `authme-api` also
produces a standalone API jar. This page lists the modules and explains how the
unified Spigot jar is built and how it picks a platform adapter at startup.

[ARCHITECTURE.md](ARCHITECTURE.md) describes the platform layer and scheduler
design; [build.md](build.md) covers build commands and JDK requirements.

## Module list

| Module | Artifact | Purpose |
| --- | --- | --- |
| `authme-api` | API jar | Public API other plugins compile against |
| `authme-core` | — | Platform-independent plugin code shared by every jar; Java 8 bytecode |
| `authme-tools` | — | Build-time tooling |
| `authme-spigot-1.8` | `<name>-<version>-Spigot-1.8.jar` | Spigot 1.8.8; Java 8 bytecode, libraries shaded into the jar |
| `authme-spigot` | `<name>-<version>-Spigot.jar` | Spigot 1.16 through 1.21.x in one jar; two compile passes (see below) |
| `authme-bungee` | Bungee jar | BungeeCord proxy plugin |
| `authme-velocity` | Velocity jar | Velocity proxy plugin |

`authme-api`, `authme-core`, `authme-tools` and `authme-spigot-1.8` form the default
reactor. The other four are added by the `java21-modules` profile, active on JDK 21+.

`authme-spigot-legacy` and `authme-spigot-1.21` no longer exist: they were merged into
the single `authme-spigot` module described below.

## The unified Spigot jar

`authme-spigot` produces one jar, `<name>-<version>-Spigot.jar`, covering Spigot 1.16
through 1.21.x. It needs Java 17 at minimum; Java 21 servers also get the dialog
adapter.

### Two-pass compile

The module compiles its sources in two passes into one output directory:

1. **Java 17 pass** (`default-compile`) — the baseline loadable on Spigot 1.16–1.20
   running Java 17, including `SpigotLegacyPlatformAdapter`.
2. **Java 21 pass** (`compile-java21`) — `Spigot121PlatformAdapter`,
   `SpigotDialogHelper`, `DialogApiAvailability` and `PlayerSignOpenListener`.

The four Java 21 classes are excluded from the first pass and included only in the
second, so they stay at Java 21 bytecode while everything else remains at Java 17.
On a Java 17 server they throw `UnsupportedClassVersionError` when loaded and are
skipped; the rest of the jar keeps working. This two-pass compile is what resolved
the earlier Java 17 vs Java 21 blocker.

`plugin.yml` declares `api-version: 1.16`, the lowest supported API, because a plugin
cannot declare a newer `api-version` than the server it runs on.

### Adapter selection

`META-INF/services/fr.xephi.authme.platform.PlatformAdapter` lists, in order:

1. `fr.xephi.authme.platform.Spigot121PlatformAdapter`
2. `fr.xephi.authme.platform.SpigotLegacyPlatformAdapter`

`AuthMe.loadPlatformAdapter()` walks those entries and returns the first compatible
adapter. On Java 21 the 1.21 adapter loads first and is selected; on Java 17 it fails
to load (`UnsupportedClassVersionError`) and is skipped, so the legacy adapter is
chosen. Feature checks such as `DialogApiAvailability` keep dialog and sign-open
behavior behind capability detection, preserving behavior across the whole
1.16–1.21.x range.

## Still-separate modules

- `authme-spigot-1.8` stays separate for Spigot 1.8.8: it is Java 8 bytecode and
  shades its libraries, since those servers cannot load libraries from `plugin.yml`.
- `authme-bungee` and `authme-velocity` are proxy plugins and do not share the Bukkit
  platform layer.
- `authme-api`, `authme-core` and `authme-tools` are not plugin deliverables;
  `authme-core` remains at Java 8 bytecode and is shared by every plugin jar.

## Constraints preserved

The consolidation kept the guardrails from the earlier plan: no behavior change and
no configuration key/default, message, permission, plugin id/name, main class or
package change; `authme-core` stays at Java 8 bytecode; Spigot 1.8 remains supported;
and Folia safety is preserved.
